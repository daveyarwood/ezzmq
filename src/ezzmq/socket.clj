(ns ezzmq.socket
  (:require [ezzmq.context :as ctx])
  (:import [org.zeromq ZMQ ZMQ$Context ZContext]))

(defprotocol SocketMaker
  (create-socket [ctx socket-type]))

(extend-protocol SocketMaker
  ZContext
  (create-socket [ctx socket-type] (.createSocket ctx socket-type))

  ZMQ$Context
  (create-socket [ctx socket-type] (.socket ctx socket-type)))

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
    (let [socket (create-socket ctx/*context* socket-type)
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

