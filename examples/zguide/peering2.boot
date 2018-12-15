#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

;; This example is not currently working. The routing bit doesn't seem to be
;; working properly.
;;
;; It seems like the main function (router) is successfully getting requests
;; from clients and forwarding them randomly to either the local or remote
;; backend, and the envelopes look correct to me, but in either case, the
;; workers never seem to get any messages; they just block waiting to receive a
;; message.
;;
;; Not sure if this is not working because of:
;; - Error on my part.
;; - Some bug in the routing logic in JeroMQ.
;; - Some weirdness around using IPC with JeroMQ. (Of note, the README says that
;;   IPC only works if everyone is using JeroMQ, but that is the case here.)
;;
;; NB: This might be related: https://github.com/zeromq/jeromq/issues/452
;;     A fix is coming soon in JeroMQ 0.4.3.
;; TODO: Try ezzmq with JeroMQ 0.4.3 and see if that fixes it.

(defn start-client
  [frontend-address log-fn]
  (zmq/worker-thread
    {:on-interrupt #(log-fn "Interrupted. Shutting down...")}
    (zmq/with-new-context
      (let [socket (zmq/socket :req {:connect frontend-address})]
        (while true
          ;; (log-fn "Sending request...")
          (zmq/send-msg socket "YO")
          (let [[msg] (zmq/receive-msg socket {:stringify true})]
            (log-fn (str "Received response: " msg))))))))

(defn start-worker
  [backend-address log-fn]
  (zmq/worker-thread
    {:on-interrupt #(log-fn "Interrupted. Shutting down...")}
    (zmq/with-new-context
      (let [socket (zmq/socket :req {:connect backend-address})]
        ;; (log-fn "Sending READY message...")
        (zmq/send-msg socket "READY")
        ;; (log-fn "Doing work...")
        (while true
          ;; (log-fn "Waiting on message...")
          (let [[id sep req] (zmq/receive-msg socket {:stringify true})]
            (log-fn (str "Received request: " req))
            (zmq/send-msg socket [id "" "SUP"])))))))

(defn endpoint-fn
  [suffix]
  (fn [id]
    (format "ipc://%s-%s.ipc" id suffix)))

(def cloud-endpoint   (endpoint-fn "cloud"))
(def localfe-endpoint (endpoint-fn "localfe"))
(def localbe-endpoint (endpoint-fn "localbe"))

(defn -main
  [self num-clients num-workers initial-delay & peers]
  (zmq/with-new-context
    (let [cloudfe  (zmq/socket :router {:bind (cloud-endpoint self)})
          cloudbe  (zmq/socket :router {:identity self
                                        :connect  (map cloud-endpoint peers)})
          localfe  (zmq/socket :router {:bind (localfe-endpoint self)})
          localbe  (zmq/socket :router {:bind (localbe-endpoint self)})
          pollerfe (zmq/poller [[localfe :pollin] [cloudfe :pollin]])
          pollerbe (zmq/poller [[localbe :pollin] [cloudbe :pollin]])
          workers  (java.util.LinkedList.)
          running? (atom true)]
      ;; Initial delay to allow time for all brokers to start.
      (Thread/sleep (Integer/parseInt initial-delay))

      ;; Start local clients and workers.
      (println "Starting clients...")
      (dotimes [i (Integer/parseInt num-clients)]
        (start-client (localfe-endpoint self)
                      #(println (format "CLIENT %d: %s" i %))))
      (println "Starting workers...")
      (dotimes [i (Integer/parseInt num-workers)]
        (start-worker (localbe-endpoint self)
                      #(println (format "WORKER %d: %s" i %))))

      (println "Brokering local/remote requests/responses...")
      (while @running?
        ;; Route backend responses to the clients that sent them.
        (if-not (zmq/polling? pollerbe)
          (reset! running? false)
          (let [timeout  (if (empty? workers) -1 1000)
                got-msgs (zmq/poll {:timeout timeout} pollerbe)
                msg      (cond
                           (contains? got-msgs localbe)
                           (let [msg (zmq/receive-msg localbe {:stringify true})]
                             (.add workers (first msg))
                             msg)

                           (contains? got-msgs cloudbe)
                           (zmq/receive-msg cloudbe {:stringify true}))]
            (when (and msg (not= "READY" (last msg)))
              ;; If the response is addressed to a peer, route it back to the
              ;; cloud. Otherwise, route it locally.
              (if-let [broker-id (->> peers
                                      (find #(= (first msg) %))
                                      first)]
                (zmq/send-msg cloudfe msg)
                (zmq/send-msg localfe msg)))))

        ;; Route as many frontend requests as we have worker capacity for.
        (if-not (and @running? (zmq/polling? pollerfe))
          (reset! running? false)
          (let [work-to-do? (atom true)]
            (while (and @work-to-do? (not (empty? workers)))
              (let [got-msgs (zmq/poll {} pollerfe)]
                (cond
                  ;; Cloud requests are always handled locally.
                  (contains? got-msgs cloudfe)
                  (let [msg (zmq/receive-msg cloudfe {:stringify true})]
                    (zmq/send-msg localbe (concat [(.poll workers) ""] msg)))

                  ;; Local requests are sometimes outsourced to a randomly
                  ;; chosen peer.
                  ;;
                  ;; (To do this properly, we would calculate cloud capacity.
                  ;; For now, we're just doing it 20% of the time at random.)
                  (contains? got-msgs localfe)
                  (let [msg (zmq/receive-msg localfe {:stringify true})]
                    (if (< (rand) 0.2)
                      (zmq/send-msg cloudbe (concat [(rand-nth peers) ""] msg))
                      (zmq/send-msg localbe (concat [(.poll workers) ""] msg))))

                  :else
                  ;; No work to do; go back to routing backend responses.
                  (reset! work-to-do? false))))))))))

