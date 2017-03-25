#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.5.3"]])

(require '[ezzmq.core :as zmq])

(defn -main
  ([]
   (println "No ports specified.")
   (System/exit 1))
  ([port]
   (println "Only one port specified; need two.")
   (System/exit 1))
  ([vent-port sink-port]
   (zmq/with-new-context
     (let [vent (zmq/socket :pull {:connect (format "tcp://*:%s" vent-port)})
           sink (zmq/socket :push {:connect (format "tcp://*:%s" sink-port)})]
       (while true
         (let [task-ms (-> (zmq/receive-msg vent :stringify true)
                           first
                           Integer/parseInt)]
           (printf "Doing a task that takes %d ms... " task-ms)
           (flush)
           (Thread/sleep task-ms)
           (println "done.")
           (zmq/send-msg sink "success")))))))
