#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(def ^:const BACKEND-ADDRESS "inproc://backend")

(defn random-id
  []
  (format "%04X-%04X" (rand-int 500) (rand-int 500)))

(defn start-client
  "Connects to the server and sends a request once per second.

   Collects responses as they arrive and prints them."
  [port log-fn]
  (zmq/worker-thread
    {:on-interrupt #(log-fn "Interrupted. Shutting down...")}
    (zmq/with-new-context
      (let [socket (zmq/socket :dealer {:connect (str "tcp://*:" port)
                                        :identity (random-id)})]
        (log-fn "Sending requests...")
        (zmq/polling {:receive-opts {:stringify true}}
          [socket :pollin [msg] (log-fn (str "Received: " msg))]
          (loop [request-number 1]
            ;; Potentially receive and print up to 100 messages in a second.
            (dotimes [_ 100] (zmq/poll {:timeout 10}))
            ;; Send a request.
            (zmq/send-msg socket (str "Request #" request-number))
            ;; Keep going as long as the poller is in good shape.
            (if (zmq/polling?)
              (recur (inc request-number))
              (log-fn "ERROR: polling interrupted."))))))))

(defn start-worker
  "Works on one request at a time and sends a random number of replies back,
   with random delays between replies."
  [log-fn]
  (zmq/worker-thread
    {:on-interrupt #(log-fn "Interrupted. Shutting down...")}
    (let [socket (zmq/socket :dealer {:connect BACKEND-ADDRESS})]
      (log-fn "Doing work...")
      (while true
        (let [msg (zmq/receive-msg socket {:stringify true})]
          (log-fn (str "Received: " msg))
          ;; Send 0-4 replies back.
          (dotimes [_ (rand-int 5)]
            ;; Simulate work that takes up to a second.
            (Thread/sleep (inc (rand-int 1000)))
            ;; Parrot the request back to the client.
            (zmq/send-msg socket msg)))))))

(defn start-server
  "Uses the multi-threaded server model to deal requests out to a pool of
   workers and route replies back to clients.

   One worker can handle one request at a time, but one client can talk to
   multiple workers at once."
  [frontend-port workers log-fn]
  (zmq/with-new-context
    (println "SERVER: Starting worker threads...")
    (dotimes [i (Integer/parseInt workers)]
      (start-worker #(println (format "WORKER %d: %s" i %))))

    (let [frontend (zmq/socket :router {:bind (str "tcp://*:" frontend-port)})
          backend  (zmq/socket :dealer {:bind BACKEND-ADDRESS})]
      (println "SERVER: Proxying messages...")
      (zmq/polling {}
        [frontend :pollin [msg]
         (zmq/send-msg backend msg)

         backend :pollin [msg]
         (zmq/send-msg frontend msg)]

        (zmq/while-polling
          (zmq/poll {:timeout 250}))))))

(defn -main
  [frontend-port workers clients]
  (println "Starting client threads...")
  (dotimes [i (Integer/parseInt clients)]
    (start-client frontend-port #(println (format "CLIENT %d: %s" i %))))
  (println "Starting server thread...")
  (start-server frontend-port workers #(println "SERVER:" %)))

