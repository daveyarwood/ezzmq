#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.4.0"]])

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
     (let [vent (zmq/socket :push {:bind (format "tcp://*:%s" vent-port)})
           sink (zmq/socket :push {:connect (format "tcp://*:%s" sink-port)})]
       (println "Press ENTER when the workers are ready.")
       (read-line)
       ; initial message to signal the start of the batch
       (zmq/send-msg sink "START BATCH")

       (let [workload (repeatedly 100 #(rand-int 100))]
         (println (format "Total expected cost: %d ms" (reduce + workload)))

         (println "Sending tasks to workers...")
         (doseq [task workload]
           (zmq/send-msg vent (str task)))))
     ; give ZeroMQ time to deliver the messages before shutting down
     (Thread/sleep 1000))))
