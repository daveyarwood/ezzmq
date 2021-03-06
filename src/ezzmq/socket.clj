(ns ezzmq.socket
  (:require [ezzmq.context :as ctx]
            [ezzmq.util    :as util])
  (:import [org.zeromq ZMQ ZMQ$Context ZContext]))

(defprotocol SocketMaker
  (create-socket [ctx socket-type]))

(extend-protocol SocketMaker
  ZContext
  (create-socket [ctx socket-type] (.createSocket ctx socket-type))

  ZMQ$Context
  (create-socket [ctx socket-type] (.socket ctx socket-type)))

;; shamelessly stolen from cljzmq
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

(defn- socket-type-lookup
  [socket-type-kw]
  (or (get socket-types socket-type-kw)
      (util/errfmt "Invalid socket type: %s" socket-type-kw)))

(defn- socket-type=
  [& args]
  (let [sts (map #(if (keyword? %) (get socket-types %) %) args)]
    (if (some #(not ((set (vals socket-types)) %)) sts)
      (util/errfmt (str "Arguments to `socket-type=` must be valid ZMQ socket "
                        "type constants or their keyword representations."))
      (apply = sts))))

(defn- socket-type-is-one-of
  [socket-type-kws socket-type]
  (contains? (set (map socket-type-lookup socket-type-kws)) socket-type))

(defn socket
  "Creates a socket and optionally does some things that you typically do with
   a socket after creating it (see options below).

   `socket-type-kw` must be one of:
   - :pair
   - :pub
   - :sub
   - :req
   - :rep
   - :xreq
   - :xrep
   - :dealer
   - :router
   - :xpub
   - :xsub
   - :pull
   - :push

   Options:
   - :identity sets the identity of the socket. The value can be a string or a
     byte array.
   - :bind/:connect specify the location on which to bind or connect the socket.
     The value must be a connection string like \"tcp://*:12345\",
     \"inproc://something\", etc.
   - :subscribe specifies one or more subscription topics when creating a SUB
     socket. By default, the socket will be subscribed to \"\", i.e. all
     messages. You can override this behavior by using this option to subscribe
     to specific topics. The value can be a string, a byte array, or a
     collection of strings or byte arrays.
   - :send-hwm/:receive-hwm set the send and receive high water mark (HWM), in
     in milliseconds.
   - :linger sets the linger period, in milliseconds."
  [socket-type-kw & [{:keys [identity
                             bind connect subscribe
                             send-hwm receive-hwm linger]}]]
  (let [socket-type (socket-type-lookup socket-type-kw)
        socket      (create-socket ctx/*context* socket-type)]
    (when identity
      (.setIdentity socket (util/as-byte-array identity)))
    (when bind
      (let [bindings (if (coll? bind) bind [bind])]
        (doseq [b bindings]
          (.bind socket b))))
    (when connect
      (let [connections (if (coll? connect) connect [connect])]
        (doseq [c connections]
          (.connect socket c))))
    (when (socket-type= :sub socket-type)
      (let [topics (if (coll? subscribe) subscribe [(or subscribe "")])]
        (doseq [topic topics]
          (.subscribe socket (util/as-byte-array topic)))))
    (when send-hwm (.setSndHWM socket send-hwm))
    (when receive-hwm (.setRcvHWM socket receive-hwm))
    (when linger (.setLinger socket linger))
    socket))

