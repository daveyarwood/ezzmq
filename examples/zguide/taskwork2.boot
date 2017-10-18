#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [vent-port sink-port ctrl-port]
  (zmq/with-new-context
    (zmq/before-shutdown
      (println "Shutting down..."))

    (let [vent     (zmq/socket :pull {:connect (str "tcp://*:" vent-port)})
          sink     (zmq/socket :push {:connect (str "tcp://*:" sink-port)})
          ctrl     (zmq/socket :sub  {:connect (str "tcp://*:" ctrl-port)})
          running? (atom true)]
      (println "Ready for work!")

      (zmq/polling {:stringify true}
        [vent :pollin [[task-ms]]
         (do
           (printf "Doing a task that takes %s ms... " task-ms)
           (flush)
           (Thread/sleep (Integer/parseInt task-ms))
           (println "done.")
           (zmq/send-msg sink "success"))

         ctrl :pollin [_]
         ;; any msg sent on the control socket is interpreted as a kill signal
         (do
           (println "Received KILL signal.")
           (reset! running? false))]

        (while (and (zmq/polling?) @running?)
          (zmq/poll 100))))))
