#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.6.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  ([]
   (println "No port specified.")
   (System/exit 1))
  ([port]
   (zmq/with-new-context
     (let [socket (zmq/socket :req {:connect (format "tcp://*:%s" port)})
           req    "Hello from client"]
       (while true
         (println "Sending msg:" req)
         (zmq/send-msg socket req)

         (let [res (zmq/receive-msg socket :stringify true)]
           (println "Received msg:" res)))))))
