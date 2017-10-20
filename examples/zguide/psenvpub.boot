#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [port]
  (zmq/with-new-context
    (let [socket (zmq/socket :pub {:bind (str "tcp://*:" port)})]
      (println "Publishing A and B messages...")
      (while true
        (zmq/send-msg socket ["A" "We don't want to see this"])
        (zmq/send-msg socket ["B" "We would like to see this"])
        (Thread/sleep 1000)))))
