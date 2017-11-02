#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.0"]])

(require '[ezzmq.core :as zmq])

;; NB 1:
;; Interrupting the process occasionally results in IndexOutOfBoundsException,
;; ArrayIndexOutOfBoundException, or the process simply hangs. I think this may
;; be the result of bug(s) in JeroMQ.
;; See: https://github.com/zeromq/jeromq/issues/488

;; NB 2:
;; For some reason, workers do not seem to be fair-queued. Instead, the same
;; worker keeps being chosen for every message, with a 1-second pause in between
;; each message as the worker simulates doing work.
;;
;; This obviously defeats the purpose of the multi-threaded server. If the
;; example worked correctly, we would see requests being received and
;; processed at roughly a rate of as many per second as there are workers.
;;
;; I tried using (org.zeromq.ZMQ/proxy frontend backend nil) in place of the
;; ezzmq polling code, and it seems to work a little better in that now 2
;; workers are used, but I still don't see every worker receiving and processing
;; requests.
;;
;; I'm going to chalk this up to jankiness somewhere in JeroMQ and move on for
;; now. At any rate, the zguide goes on to describe better patterns wherein you
;; manage your own queue of workers, and I've used those patterns in production
;; to good effect. In other words, perhaps it's not so important that the
;; fair-queuing mechanism is implemented correctly in JeroMQ, when it's not
;; recommended to rely on it anyway.

(defn -main
  [port workers]
  (zmq/with-new-context
    (zmq/before-shutdown
      (println "Shutting down..."))
    (zmq/after-shutdown
      (println "Done."))

    (println "Starting worker threads...")
    (dotimes [i (Integer/parseInt workers)]
      (let [worker-log #(println (format "WORKER %d: %s" i %))]
        (zmq/worker-thread
          {:on-interrupt #(worker-log "Interrupted. Shutting down...")}
          (let [socket (zmq/socket :rep {:connect "inproc://workers"})
                res    "Hello from worker"]
            (worker-log "Waiting for requests...")
            (while true
              (let [req (zmq/receive-msg socket {:stringify true})]
                (worker-log (str "Received msg: " req))
                (Thread/sleep 1000)) ; simulate doing some work

              ;; (worker-log (str "Sending msg: " res))
              (zmq/send-msg socket res))))))
    (let [frontend (zmq/socket :router {:bind (str "tcp://*:" port)})
          backend  (zmq/socket :dealer {:bind "inproc://workers"})]
      (println "SERVER: Proxying messages...")

      #_(org.zeromq.ZMQ/proxy frontend backend nil)

      (let [in-poller  (zmq/create-poller ezzmq.context/*context* 2)
            out-poller (zmq/create-poller ezzmq.context/*context* 2)]
        (.register in-poller  frontend (:pollin zmq/poll-types))
        (.register in-poller  backend  (:pollin zmq/poll-types))
        (.register out-poller frontend (:pollout zmq/poll-types))
        (.register out-poller backend  (:pollout zmq/poll-types))
        (while true
          (.poll in-poller)
          (.poll out-poller)
          (when (and (.pollin in-poller 0)
                     (.pollout out-poller 1))
            (let [msg (zmq/receive-msg frontend)]
              (zmq/send-msg backend msg)))
          (when (and (.pollin in-poller 1)
                     (.pollout out-poller 0))
            (let [msg (zmq/receive-msg backend)]
              (zmq/send-msg frontend msg)))))

      #_(zmq/polling {}
        [frontend :pollin [msg]
         (do
           (prn :frontend (let [[id sep msg] msg]
                            [(map int id) (String. sep) (String. msg)]))
           (zmq/send-msg backend msg))

         backend :pollin [msg]
         (do
           (prn :backend (let [[id sep msg] msg]
                           [(map int id) (String. sep) (String. msg)]))
           (zmq/send-msg frontend msg))]

        (zmq/while-polling
          (zmq/poll))))))
