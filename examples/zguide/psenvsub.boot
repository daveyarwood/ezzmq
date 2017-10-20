#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.1"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [port]
  (zmq/with-new-context
    (let [socket (zmq/socket :sub {:connect   (str "tcp://*:" port)
                                   :subscribe "B"})]
      (println "Receiving B messages...")
      (while true
        (let [[topic contents] (zmq/receive-msg socket {:stringify true})]
          (println (format "[%s] %s" topic contents)))))))
