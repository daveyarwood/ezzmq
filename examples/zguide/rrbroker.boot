#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [frontend-port backend-port]
  (zmq/with-new-context
    (let [frontend (zmq/socket :router {:bind (str "tcp://*:" frontend-port)})
          backend  (zmq/socket :dealer {:bind (str "tcp://*:" backend-port)})]
      (println "Proxying messages...")
      (zmq/polling {}
        [frontend :pollin [msg]
         (zmq/send-msg backend msg)

         backend :pollin [msg]
         (zmq/send-msg frontend msg)]

        (zmq/while-polling
          (zmq/poll))))))
