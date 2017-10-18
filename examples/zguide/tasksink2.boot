#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [sink-port ctrl-port]
  (zmq/with-new-context
    (let [sink (zmq/socket :pull {:bind (str "tcp://*:" sink-port)})
          ctrl (zmq/socket :pub  {:bind (str "tcp://*:" ctrl-port)})]
      (println "Waiting for a message signaling start of batch...")
      (zmq/receive-msg sink)
      (println "Signal received. Collecting results...")

      (let [start (System/currentTimeMillis)]
        (dotimes [n 100]
          (let [result (-> (zmq/receive-msg sink {:stringify true}) first)]
            (println (format "Task #%d result: %s" (inc n) result))))
        (let [end (System/currentTimeMillis)]
          (println (format "Total elapsed time: %d ms" (- end start)))))

      (println "Sending KILL signal to workers...")
      (zmq/send-msg ctrl "KILL")
      ;; give the message some time to deliver
      (Thread/sleep 1000))))
