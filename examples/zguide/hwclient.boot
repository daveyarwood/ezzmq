#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [port]
  (zmq/with-new-context
    (let [socket (zmq/socket :req {:connect (str "tcp://*:" port)})
          req    "Hello from client"]
      (while true
        ;; (println "Sending msg:" req)
        (zmq/send-msg socket req)

        (let [res (zmq/receive-msg socket {:stringify true})]
          (println "Received msg:" res))))))
