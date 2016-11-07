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
  ([port1 port2]
   (zmq/with-new-context
     (let [pull     (zmq/socket :pull {:connect (format "tcp://*:%s" port1)})
           sub      (zmq/socket :sub  {:connect (format "tcp://*:%s" port2)
                                       :subscribe "10001"})
           polling? (atom true)]
       ; necessary because of JeroMQ issue #380
       (zmq/before-shutdown (reset! polling? false))
       (zmq/polling {:stringify true}
         [; connect to task ventilator
          pull :pollin [msg]
          (println (format "PULL: got msg: %s" msg))

          ; connect to weather server
          sub :pollin [msg]
          (println (format "SUB: got msg: %s" msg))]
         (while @polling?
           (zmq/poll)))))))
