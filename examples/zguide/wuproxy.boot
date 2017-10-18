#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [frontend-port backend-port]
  (zmq/with-new-context
    ;; NB: The zguide diagram and C example use XSUB and XPUB, whereas the Java
    ;; example uses PUB and SUB. I tried using XSUB and XPUB here and they
    ;; don't seem to work for reasons unknown. Maybe a bug in JeroMQ?
    (let [frontend (zmq/socket :sub {:connect (str "tcp://*:" frontend-port)})
          backend  (zmq/socket :pub {:bind (str "tcp://*:" backend-port)})]
      (println "Proxying weather updates...")
      (zmq/polling {}
        [frontend :pollin [msg]
         (zmq/send-msg backend msg)]
        (zmq/while-polling
          (zmq/poll))))))
