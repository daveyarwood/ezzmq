#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  []
  (zmq/with-new-context
    (let [location   "inproc://identity-example"
          router     (zmq/socket :router {:bind location})
          anonymous  (zmq/socket :req {:connect location})
          identified (zmq/socket :req {:identity "surfbort" :connect location})]
      (zmq/send-msg anonymous "generate me an identity plz")
      (let [[id _ msg] (zmq/receive-msg router {:stringify true})]
        (println (format "%s said: %s" id msg)))
      (zmq/send-msg identified "hello, i am surfbort")
      (let [[id _ msg] (zmq/receive-msg router {:stringify true})]
        (println (format "%s said: %s" id msg))))))
