(ns ezzmq.message
  (:import [org.zeromq ZMQ$Socket ZFrame]))

(defmacro with-send-timeout
  "Creates a scope where `socket` has a send timeout of `timeout` ms.

   If `timeout` is nil, the existing value is used."
  [socket timeout & body]
  `(let [previous-timeout# (.getSendTimeOut ~socket)]
     (try
       (.setSendTimeOut ~socket (or ~timeout previous-timeout#))
       ~@body
       (finally
         (.setSendTimeOut ~socket previous-timeout#)))))

(defmacro with-receive-timeout
  "Creates a scope where `socket` has a receive timeout of `timeout` ms.

   If `timeout` is nil, the existing value is used."
  [socket timeout & body]
  `(let [previous-timeout# (.getReceiveTimeOut ~socket)]
     (try
       (.setReceiveTimeOut ~socket (or ~timeout previous-timeout#))
       ~@body
       (finally
         (.setReceiveTimeOut ~socket previous-timeout#)))))

(defn receive-msg
  "Receives an entire message. This means receiving one frame, then checking to
   see if there is more until there are no more frames to receive.

   Returns the result as a vector of byte arrays, each representing one frame.

   Options:
   - :stringify makes it so that the frames are returned as strings. Otherwise,
     the frames are returned as byte arrays.
   - :timeout sets the amount of time (in ms) to wait for a message to be sent
     before giving up.

     A timeout of 0 will return immediately.

     A timeout of -1 (default) will block until the message is sent."
  [^ZMQ$Socket socket & {:keys [stringify timeout]}]
  (with-receive-timeout socket timeout
    (loop [frames [(.recv socket)]
           more? (.hasReceiveMore socket)]
      (if more?
        (recur (conj frames (.recv socket))
               (.hasReceiveMore socket))
        (if stringify
          (mapv #(String. %) frames)
          frames)))))

(defprotocol Sendable
  (zframes [x]))

(extend-protocol Sendable
  (Class/forName "[B") ; byte array
  (zframes [ba] [(ZFrame. ba)])

  String
  (zframes [s] [(ZFrame. s)])

  clojure.lang.Sequential
  (zframes [coll] (mapv #(ZFrame. %) coll)))

(defn send-msg
  "Sends a message. The input to this function can be:

   - a string
   - a byte array
   - a sequence of any number of strings and byte arrays

   Each string/byte array becomes a ZFrame in the message.

   Options:
   - :timeout sets the amount of time (in ms) to wait for a message to be sent
     before giving up.

     A timeout of 0 will return immediately.

     A timeout of -1 (default) will block until the message is sent."
  [socket msg & {:keys [timeout]}]
  (let [frames (zframes msg)]
    (with-send-timeout socket timeout
      (doseq [frame (butlast frames)]
        (.send frame socket ZFrame/MORE))
      (.send (last frames) socket 0))))
