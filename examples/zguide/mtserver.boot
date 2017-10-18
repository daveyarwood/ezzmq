#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  [port]
  (zmq/with-new-context
    ;; Interrupting the process occasionally results in
    ;; IndexOutOfBoundsException, ArrayIndexOutOfBoundException, or the process
    ;; simply hangs. I think this may be the result of bug(s) in JeroMQ.
    ;; See: https://github.com/zeromq/jeromq/issues/488
    (zmq/before-shutdown
      (println "Shutting down..."))
    (zmq/after-shutdown
      (println "Done."))

    (println "Starting worker threads...")
    (dotimes [i 5]
      (let [worker-log #(println (format "WORKER %d: %s" i %))]
        (zmq/worker-thread
          {:on-interrupt #(worker-log "WORKER: Interrupted. Shutting down...")}
          (let [socket (zmq/socket :rep {:connect "inproc://workers"})
                res    "Hello from worker"]
            (worker-log "Waiting for requests...")
            (while true
              (let [req (zmq/receive-msg socket {:stringify true})]
                (worker-log (str "Received msg: " req))
                (Thread/sleep 1000)) ; simulate doing some work

              (worker-log (str "Sending msg: " res))
              (zmq/send-msg socket res))))))
    (let [frontend (zmq/socket :router {:bind (str "tcp://*:" port)})
          backend  (zmq/socket :dealer {:bind "inproc://workers"})]
      (println "SERVER: Proxying messages...")
      (zmq/polling {}
        [frontend :pollin [msg]
         (zmq/send-msg backend msg)

         backend :pollin [msg]
         (zmq/send-msg frontend msg)]

        (zmq/while-polling
          (zmq/poll))))))
