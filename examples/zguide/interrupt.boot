#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.1.3"]])

(require '[ezzmq.core :as zmq])

(def ^:const SOCKET-ADDRESS "inproc://testing123")

(defn -main
  []
  (binding [zmq/*context-type* :zmq-context]
    (zmq/with-new-context
      (future
        (println "SERVER: Starting server...")
        (let [server (zmq/socket :rep {:bind SOCKET-ADDRESS})]
          (println "SERVER: Blocking as I wait for a message...")
          (while true
            (try
              (zmq/receive-msg server)
              (catch org.zeromq.ZMQException e
                (let [ETERM (.getCode org.zeromq.ZMQ$Error/ETERM)]
                  (if (= ETERM (.getErrorCode e))
                    (println "SERVER: ETERM caught!")
                    (throw e)))))
            (Thread/sleep 1000))))

      (.addShutdownHook (Runtime/getRuntime)
        (let [ctx zmq/*context*] ; pass in the value bound by with-new-context
          (Thread.
            (fn []
              (println "Interrupt received. Shutting down...")
              (zmq/destroy-context! ctx) ; this interrupts the worker thread

              (println)
              (println "Calling shutdown-agents...")
              (shutdown-agents)

              (println "Done.")))))

      (Thread/sleep 100)
      (println \newline "Waiting for Ctrl-C..." \newline)
      (while true (Thread/sleep 100)))))
