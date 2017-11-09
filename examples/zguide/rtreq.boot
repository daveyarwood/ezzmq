#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(defn random-id
  []
  (format "%04X-%04X" (rand-int 500) (rand-int 500)))

(defn start-worker
  [port log-fn]
  (zmq/with-new-context
    (zmq/worker-thread
      {:on-interrupt #(log-fn "Interrupted. Shutting down...")}
      (let [socket (zmq/socket :req {:connect (str "tcp://*:" port)
                                     :identity (random-id)})]
        (log-fn "Doing work...")
        (loop [tasks-completed 0]
          (zmq/send-msg socket "Ready for work!")
          (let [[workload] (zmq/receive-msg socket {:stringify true})]
            ;; (log-fn (str "Received msg: " workload))
            (if (= "FIRED" workload)
              (log-fn (format "Completed: %d tasks" tasks-completed))
              (do
                ;; simulate doing some work
                (Thread/sleep (inc (rand-int 500)))
                (recur (inc tasks-completed))))))))))

(defn -main
  [port workers]
  (println "Starting worker threads...")
  (dotimes [i (Integer/parseInt workers)]
    (start-worker port #(println (format "WORKER %d: %s" i %))))
  (zmq/with-new-context
    (let [socket (zmq/socket :router {:bind (str "tcp://*:" port)})
          end    (+ (System/currentTimeMillis) 5000)]
      (println "SERVER: Handing tasks to available workers...")
      (while (pos? (- end (System/currentTimeMillis)))
        (let [[id sep msg] (zmq/receive-msg socket {:stringify true})]
          (zmq/send-msg socket [id "" "WORK"])))
      (println "SERVER: Time's up, firing workers...")
      (dotimes [_ (Integer/parseInt workers)]
        (let [[id sep msg] (zmq/receive-msg socket {:stringify true})]
          (zmq/send-msg socket [id "" "FIRED"]))))))

