#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [port]
  (zmq/with-new-context
    (let [socket (zmq/socket :rep {:connect (str "tcp://*:" port)})
          res    "Hello from worker"]
      (println "Waiting for requests...")
      (while true
        (let [req (zmq/receive-msg socket {:stringify true})]
          (println "Received msg:" req)
          (Thread/sleep 1000)) ; simulate doing some work

        ;; (println "Sending msg:" res)
        (zmq/send-msg socket res)))))
