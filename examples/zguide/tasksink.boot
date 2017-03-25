#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.5.2"]])

(require '[ezzmq.core :as zmq])

(defn -main
  ([]
   (println "No port specified.")
   (System/exit 1))
  ([port]
   (zmq/with-new-context
     (let [sink (zmq/socket :pull {:bind (format "tcp://*:%s" port)})]
       ; wait for start of batch
       (zmq/receive-msg sink)

       (let [start (System/currentTimeMillis)]
         (dotimes [n 100]
           (let [result (-> (zmq/receive-msg sink :stringify true) first)]
             (println (format "Task #%d result: %s" (inc n) result))))
         (let [end (System/currentTimeMillis)]
           (println (format "Total elapsed time: %d ms" (- end start)))))))))
