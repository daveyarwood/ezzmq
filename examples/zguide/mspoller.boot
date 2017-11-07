#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [port1 port2]
  (zmq/with-new-context
    (let [pull (zmq/socket :pull {:connect (str "tcp://*:" port1)})
          sub  (zmq/socket :sub  {:connect (str "tcp://*:" port2)
                                  :subscribe "10001"})]
      (zmq/polling {:receive-opts {:stringify true}}
        [pull :pollin [msg] ; connect to task ventilator
         (println (format "PULL: got msg: %s" msg))

         sub :pollin [msg]  ; connect to weather server
         (println (format "SUB: got msg: %s" msg))]

        (zmq/while-polling
          (zmq/poll))))))
