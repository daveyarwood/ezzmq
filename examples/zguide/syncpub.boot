#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [pub-port sync-port subscribers-expected messages-to-send & [delay-ms]]
  (when delay-ms (Thread/sleep (Integer/parseInt delay-ms)))
  (zmq/with-new-context
    (let [pub (zmq/socket :pub {:bind     (str "tcp://*:" pub-port)
                                :linger   5000
                                :send-hwm 0})
          rep (zmq/socket :rep {:bind (str "tcp://*:" sync-port)})]
      (println "Waiting for subscribers...")
      (loop [subscribers 0]
        (when (< subscribers (Integer/parseInt subscribers-expected))
          (zmq/receive-msg rep)
          (zmq/send-msg rep "Subscriber counted.")
          (recur (inc subscribers))))
      (println "Broadcasting" messages-to-send "messages...")
      (dotimes [n (Integer/parseInt messages-to-send)]
        (zmq/send-msg pub (str "Message " n)))
      (println "Messages sent. Sending END message.")
      (zmq/send-msg pub "END"))))
