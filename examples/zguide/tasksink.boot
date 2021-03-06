#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [port]
  (zmq/with-new-context
    (let [sink (zmq/socket :pull {:bind (str "tcp://*:" port)})]
      (println "Waiting for a message signaling start of batch...")
      (zmq/receive-msg sink)
      (println "Signal received. Collecting results...")

      (let [start (System/currentTimeMillis)]
        (dotimes [n 100]
          (let [result (-> (zmq/receive-msg sink {:stringify true}) first)]
            (println (format "Task #%d result: %s" (inc n) result))))
        (let [end (System/currentTimeMillis)]
          (println (format "Total elapsed time: %d ms" (- end start))))))))
