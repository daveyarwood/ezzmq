#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.1.1"]])

(require '[ezzmq.core :as zmq])

(defn fake-weather-update-msg
  []
  (let [zip-code (+ 10000 (rand-int 20000))
        temp     (-> (rand-int 215) (- 80) (+ 1))
        humidity (-> (rand-int 50) (+ 10) (+ 1))]
    (format "%05d %d %d" zip-code temp humidity)))

(defn -main
  ([]
   (println "No port specified.")
   (System/exit 1))
  ([port]
   (zmq/with-new-context
     (let [socket (zmq/socket :pub {:bind (format "tcp://*:%s" port)})]
       (while true
         (let [msg (fake-weather-update-msg)]
           (println "Sending msg:" msg)
           (zmq/send-msg socket msg)))))))
