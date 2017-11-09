#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [vent-port sink-port]
  (zmq/with-new-context
    (let [vent (zmq/socket :pull {:connect (str "tcp://*:" vent-port)})
          sink (zmq/socket :push {:connect (str "tcp://*:" sink-port)})]
      (println "Ready for work!")
      (while true
        (let [task-ms (-> (zmq/receive-msg vent {:stringify true})
                          first
                          Integer/parseInt)]
          (printf "Doing a task that takes %d ms... " task-ms)
          (flush)
          (Thread/sleep task-ms)
          (println "done.")
          (zmq/send-msg sink "success"))))))
