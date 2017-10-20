#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [sub-port sync-port]
  (zmq/with-new-context
    (let [sub (zmq/socket :sub {:connect     (str "tcp://*:" sub-port)
                                :receive-hwm 0})
          req (zmq/socket :req {:connect (str "tcp://*:" sync-port)})]
      ;; Give the SUB socket a little time to connect.
      (Thread/sleep 1000)
      (println "Notifying publisher that I'm ready for messages.")
      (zmq/send-msg req "Ready for messages.")
      (zmq/receive-msg req) ; wait for reply
      (println "Receiving messages...")
      (loop [messages  0
             [message] (zmq/receive-msg sub {:stringify true})]
        (if (= "END" message)
          (println "Received" messages "messages.")
          (recur (inc messages)
                 (zmq/receive-msg sub {:stringify true})))))))
