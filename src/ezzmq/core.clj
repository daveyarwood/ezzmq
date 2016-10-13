(ns ezzmq.core
  (:import [org.zeromq ZMQ ZContext ZMsg]))

;;; CONTEXT ;;;

(def ^:dynamic *context* nil)
(def ^:dynamic *context-type* :zcontext)

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
  (destroy-context! [ctx] (.term ctx)))

(defmacro with-zmq-context
  "Executes `body` given an existing ZMQ context `ctx`.

   When done, closes all sockets and destroys the context."
  [ctx & body]
  `(binding [*context* ~ctx]
     ~@body
     (destroy-context! *context*)))

(defmacro with-new-zmq-context
  "Executes `body` using a one-off ZMQ context.

   When done, closes all sockets and destroys the context."
  [& body]
  `(binding [*context* (zmq-context)]
     ~@body
     (destroy-context! *context*)))

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
      (when bind (.bind socket bind))
      (when connect (.connect socket connect))
      (when subscribe
        (let [topics (if (coll? subscribe) subscribe [subscribe])]
          (doseq [x topics]
            (let [topic (if (string? x) (.getBytes x) x)]
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
