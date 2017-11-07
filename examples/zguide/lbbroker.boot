#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.0"]])

(require '[ezzmq.core :as zmq])

(defn random-id
  []
  (format "%04X-%04X" (rand-int 500) (rand-int 500)))

(def frontend-address "ipc://frontend.ipc")
(def backend-address  "ipc://backend.ipc")

;; Incremented each time a client receives a response.
;; We exit after we have one response per client.
(def response-count (atom 0))

(defn start-client
  [log-fn]
  (zmq/with-new-context
    (zmq/worker-thread
      {:on-interrupt #(log-fn "Interrupted. Shutting down...")}
      (let [socket (zmq/socket :req {:connect frontend-address
                                     :identity (random-id)})
            ;; _      (log-fn "Sending request...")
            _      (zmq/send-msg socket "YO")
            [msg]  (zmq/receive-msg socket {:stringify true})]
        (log-fn (str "Received response: " msg))
        (swap! response-count inc)))))

(defn start-worker
  [log-fn]
  (zmq/with-new-context
    (zmq/worker-thread
      {:on-interrupt #(log-fn "Interrupted. Shutting down...")}
      (let [socket (zmq/socket :req {:connect backend-address
                                     :identity (random-id)})]
        ;; (log-fn "Sending READY message...")
        (zmq/send-msg socket "READY")
        ;; (log-fn "Doing work...")
        (while true
          (let [[id sep req] (zmq/receive-msg socket {:stringify true})]
            (log-fn (str "Received request: " req))
            (zmq/send-msg socket [id "" "SUP"])))))))

(defn -main
  [workers clients]
  (println "Starting worker threads...")
  (dotimes [i (Integer/parseInt workers)]
    (start-worker #(println (format "WORKER %d: %s" i %))))

  (println "Starting client threads...")
  (dotimes [i (Integer/parseInt clients)]
    (start-client #(println (format "CLIENT %d: %s" i %))))

  (zmq/with-new-context
    (let [frontend       (zmq/socket :router {:bind frontend-address})
          backend        (zmq/socket :router {:bind backend-address})
          workers        (java.util.LinkedList.)
          ;; Worker message is either "READY"  or a response addressed to the
          ;; client who sent the request.
          backend-fn     (fn [[worker-id sep & more]]
                           ;; In either case, add the worker to the queue of
                           ;; available workers.
                           (.add workers worker-id)
                           ;; If it's a response, send it to the frontend.
                           (when (not= "READY" (first more))
                             (let [[client-id sep response] more]
                               (zmq/send-msg
                                 frontend
                                 [client-id "" response]))))
          ;; Take the least recently used (LRU) worker from the queue and
          ;; forward it the client's request.
          frontend-fn    (fn [[client-id sep request]]
                           (let [worker-id (.poll workers)]
                             (zmq/send-msg
                               backend
                               [worker-id "" client-id "" request])))
          ;; For polling the backend only, when there are no available workers.
          backend-poller (zmq/poller [[backend :pollin backend-fn]])
          ;; For polling the backend and frontend, when we know we have at least
          ;; one worker available.
          both-poller    (zmq/poller [[backend :pollin backend-fn]
                                      [frontend :pollin frontend-fn]])
          running?       (atom true)]
      (while @running?
        (let [poller (if (pos? (count workers))
                       both-poller
                       backend-poller)]
          (cond
            (= @response-count (Integer/parseInt clients))
            (do
              (println "Responses received:" @response-count)
              (reset! running? false))

            (zmq/polling? poller)
            (zmq/poll {:timeout 1000} poller)

            :else
            (do
              (println "ERROR: Polling interrupted.")
              (reset! running? false))))))))

