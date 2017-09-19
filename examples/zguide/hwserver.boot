#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  ([]
   (println "No port specified.")
   (System/exit 1))
  ([port]
   (zmq/with-new-context
     (let [socket (zmq/socket :rep {:bind (format "tcp://*:%s" port)})
           res    "Hello from server"]
       (println "Waiting for requests...")
       (while true
         (let [req (zmq/receive-msg socket {:stringify true})]
           (println "Received msg:" req)
           (Thread/sleep 1000)) ; simulate doing some work

         (println "Sending msg:" res)
         (zmq/send-msg socket res))))))
