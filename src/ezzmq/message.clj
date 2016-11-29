(ns ezzmq.message
  (:import [org.zeromq ZMQ$Socket ZFrame]))

(defn receive-msg
  "Receives an entire message. This means receiving one frame, then checking to
   see if there is more until there are no more frames to receive.

   Returns the result as a vector of byte arrays, each representing one frame."
  [^ZMQ$Socket socket & {:keys [stringify]}]
  (loop [frames [(.recv socket)]
         more? (.hasReceiveMore socket)]
    (if more?
      (recur (conj frames (.recv socket))
             (.hasReceiveMore socket))
      (if stringify
        (mapv #(String. %) frames)
        frames))))

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

   Each string/byte array becomes a ZFrame in the message."
  ([socket msg]
   (let [frames (zframes msg)]
     (doseq [frame (butlast frames)]
       (.send frame socket ZFrame/MORE))
     (.send (last frames) socket 0))))
