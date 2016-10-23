#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.2.0"]])

(require '[ezzmq.core :as zmq])

(def ^:const SOCKET-ADDRESS "inproc://testing123")

(defn -main
  []
  (binding [zmq/*context-type* :zmq-context]
    (zmq/with-new-context
      (zmq/before-shutdown
        (println "Interrupt received. Shutting down..."))

      (zmq/after-shutdown
        (println "Done."))

      (zmq/worker-thread {:on-interrupt #(println "SERVER: ETERM caught!")}
        (println "SERVER: Starting server...")
        (let [server (zmq/socket :rep {:bind SOCKET-ADDRESS})]
          (println "SERVER: Blocking as I wait for a message...")
          (while true
            (zmq/receive-msg server)
            (Thread/sleep 1000))))

      (Thread/sleep 100)
      (println \newline "Waiting for Ctrl-C..." \newline)
      (while true (Thread/sleep 100)))))