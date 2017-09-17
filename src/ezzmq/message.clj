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
  [^ZMQ$Socket socket & [{:keys [stringify timeout]}]]
  (with-receive-timeout socket timeout
    (when-let [first-frame (.recv socket)]
      (loop [frames [first-frame]
             more?  (.hasReceiveMore socket)]
        (if more?
          (recur (conj frames (.recv socket))
                 (.hasReceiveMore socket))
          (if stringify
            (mapv #(String. %) frames)
            frames))))))

(defmacro if-msg
  "Does a (by default) non-blocking receive on `socket`.

   If a message is received, runs the `then` branch with the received message
   bound to `msg-var`.

   If no message is received, runs the `else` branch.

   Options are the same as those for `receive-msg`."
  [socket opts [msg-var] then else]
  `(if-let [~msg-var (receive-msg ~socket (merge {:timeout 0} ~opts))]
     ~then
     ~else))

(defmacro when-msg
  "Does a (by default) non-blocking receive on `socket`.

   If a message is received, runs the `body` with the received message bound to
   `msg-var`.

   Options are the same as those for `receive-msg`."
  [socket opts [msg-var] & body]
  `(when-let [~msg-var (receive-msg ~socket (merge {:timeout 0} ~opts))]
     ~@body))

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
  [socket msg & [{:keys [timeout]}]]
  (let [frames (zframes msg)]
    (with-send-timeout socket timeout
      (doseq [frame (butlast frames)]
        (.send frame socket ZFrame/MORE))
      (.send (last frames) socket 0))))
