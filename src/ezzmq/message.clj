(ns ezzmq.message
  (:import [org.zeromq ZMQ$Socket ZMsg]))

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
   (add-to-zmsg msg ^ZMsg zmsg)
   (.send zmsg socket)))

