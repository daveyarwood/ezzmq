(ns ezzmq.core
  (:require [clojure.set :as set]
            [wall.hack])
  (:import  [org.zeromq ZMQ ZContext ZMsg]))

;;; CONTEXT ;;;

(def ^:dynamic *context* nil)
(def ^:dynamic *context-type* :zcontext)

(def ^:dynamic *before-shutdown-fns* {})
(def ^:dynamic *after-shutdown-fns* {})

(defmacro before-shutdown
  [& body]
  `(alter-var-root #'*before-shutdown-fns*
                   update *context* conj (fn [] ~@body)))

(defmacro after-shutdown
  [& body]
  `(alter-var-root #'*after-shutdown-fns*
                   update *context* conj (fn [] ~@body)))

(defn context
  "Returns a new ZMQ context."
  []
  (case *context-type*
    :zcontext    (ZContext. 1)
    :zmq-context (ZMQ/context 1)
    (throw (Exception. (format "Invalid context type: %s" *context-type*)))))

(defprotocol SocketMaker
  (create-socket [ctx socket-type]))

(defprotocol Destroyable
  (destroy-context! [ctx]))

(extend-type ZContext
  SocketMaker
  (create-socket [ctx socket-type] (.createSocket ctx socket-type))

  Destroyable
  (destroy-context! [ctx] (.destroy ctx)))

(extend-type org.zeromq.ZMQ$Context
  SocketMaker
  (create-socket [ctx socket-type] (.socket ctx socket-type))

  Destroyable
  (destroy-context! [context]
    ; ZMQ$Contexts don't automatically close their sockets when you terminate
    ; them; they block until you do it manually.
    ;
    ; The array of sockets is hidden inside of an inner Ctx instance, and both
    ; of these fields are private, so we have to use wallhaxx to get to them.
    (let [ctx     (wall.hack/field org.zeromq.ZMQ$Context :ctx context)
          sockets (wall.hack/field zmq.Ctx :sockets ctx)]
      (doseq [socket sockets]
        (doto socket
          (.setSocketOpt zmq.ZMQ/ZMQ_LINGER (int 0))
          (.close))))
    (.term context)))

(defn shut-down-context!
  [ctx]
  (let [before-fns (get *before-shutdown-fns* ctx [])
        after-fns  (get *after-shutdown-fns* ctx [])]
    (doseq [f before-fns] (f))
    (destroy-context! ctx)
    (doseq [f after-fns] (f))
    (alter-var-root #'*before-shutdown-fns* dissoc ctx)
    (alter-var-root #'*after-shutdown-fns* dissoc ctx)))

; Ensures that on shutdown, any "active" contexts are shut down, and their
; before- and after-shutdown hooks are called.
(.addShutdownHook (Runtime/getRuntime)
  (let [ctx        *context*
        before-fns (get *before-shutdown-fns* ctx [])
        after-fns  (get *after-shutdown-fns* ctx [])]
    (Thread.
      (fn []
        (doseq [ctx (set/union (set (keys *before-shutdown-fns*))
                               (set (keys *after-shutdown-fns*)))]
          (shut-down-context! ctx))))))

(defmacro with-context
  "Executes `body` given an existing ZMQ context `ctx`.

   When done, closes all sockets and destroys the context."
  [ctx & body]
  `(binding [*context* ~ctx]
     (alter-var-root #'*before-shutdown-fns* assoc *context* [])
     (alter-var-root #'*after-shutdown-fns* assoc *context* [])
     ~@body
     (shut-down-context! *context*)))

(defmacro with-new-context
  "Executes `body` using a one-off ZMQ context.

   When done, closes all sockets and destroys the context."
  [& body]
  `(binding [*context* (context)]
     (alter-var-root #'*before-shutdown-fns* assoc *context* [])
     (alter-var-root #'*after-shutdown-fns* assoc *context* [])
     ~@body
     (shut-down-context! *context*)))

;;; SOCKETS ;;;

; shamelessly stolen from cljzmq
(def ^:const socket-types
  {:pair   ZMQ/PAIR
   :pub    ZMQ/PUB
   :sub    ZMQ/SUB
   :req    ZMQ/REQ
   :rep    ZMQ/REP
   :xreq   ZMQ/XREQ
   :xrep   ZMQ/XREP
   :dealer ZMQ/DEALER
   :router ZMQ/ROUTER
   :xpub   ZMQ/XPUB
   :xsub   ZMQ/XSUB
   :pull   ZMQ/PULL
   :push   ZMQ/PUSH})

(defn socket
  [socket-type & [opts]]
  (if-let [socket-type (get socket-types socket-type)]
    (let [socket (create-socket *context* socket-type)
          {:keys [bind connect subscribe]} opts]
      (when bind
        (let [bindings (if (coll? bind) bind [bind])]
          (doseq [b bindings]
            (.bind socket b))))
      (when connect
        (let [connections (if (coll? connect) connect [connect])]
          (doseq [c connections]
            (.connect socket c))))
      (when subscribe
        (let [topics (if (coll? subscribe) subscribe [subscribe])]
          (doseq [t topics]
            (let [topic (if (string? t) (.getBytes t) t)]
              (.subscribe socket topic)))))
      socket)
    (throw (Exception. (format "Invalid socket type: %s" socket-type)))))

;;; MESSAGES ;;;

(defn receive-msg
  "Receives an entire message. This means receiving one frame, then checking to
   see if there is more until there are no more frames to receive.

   Returns the result as a vector of byte arrays, each representing one frame."
  [socket & {:keys [stringify]}]
  (loop [frames [(.recv socket)]
         more? (.hasReceiveMore socket)]
    (if more?
      (recur (conj frames (.recv socket))
             (.hasReceiveMore socket))
      (if stringify
        (mapv #(String. %) frames)
        frames))))

(defprotocol Sendable
  (add-to-zmsg [x zmsg]))

(extend-protocol Sendable
  (Class/forName "[B") ; byte array
  (add-to-zmsg [ba zmsg] (.add zmsg ba))

  String
  (add-to-zmsg [s zmsg] (.addString zmsg s))

  clojure.lang.Sequential
  (add-to-zmsg [coll zmsg] (doseq [x coll] (add-to-zmsg x zmsg))))

(defn send-msg
  "Sends a ZMsg. The input to this function can be:

   - a string
   - a byte array
   - a sequence of any number of strings and byte arrays

   Each string/byte array becomes a frame in the ZMsg."
  ([socket msg]
   (send-msg socket msg (ZMsg.)))
  ([socket msg zmsg]
   (add-to-zmsg msg zmsg)
   (.send zmsg socket)))

;;; WORKER THREADS ;;;

(defmacro worker-thread
  [{:keys [on-interrupt] :as opts} & body]
  `(future
     (try
       ~@body
       (catch org.zeromq.ZMQException e#
         (let [ETERM# (.getCode org.zeromq.ZMQ$Error/ETERM)]
           (if (= ETERM# (.getErrorCode e#))
             ((or ~on-interrupt #()))
             (throw e#)))))))

