#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(def ^:const REQUEST_TIMEOUT 2500) ; ms
(def ^:const REQUEST_RETRIES 5)

(def request-number (atom 0))

(let [socket (atom nil)]
  (defn make-socket
    [port]
    (swap! socket (fn [socket]
                    (when-not (nil? socket)
                      (.close socket))
                    (zmq/socket :req {:connect (str "tcp://*:" port)})))))

;; TODO: upgrade to JeroMQ 0.4.3 and close the previous poller each time
;; this function is called, like `make-socket` above.
(defn make-poller
  [socket]
  (zmq/poller [[socket :pollin]]))

(defn -main
  [port]
  (zmq/with-new-context
    (loop [retries  REQUEST_RETRIES
           socket   (make-socket)
           poller   (make-poller socket)]
      (let [n          @request-number
            _          (zmq/send-msg socket (str n))
            got-msgs   (zmq/poll {:timeout REQUEST_TIMEOUT} poller)]
        (cond
          (not (zmq/polling? poller))
          :break

          (contains? got-msgs socket)
          (let [[reply] (zmq/receive-msg socket {:stringify true})]
            (if (= n reply)
              (do
                (println "Server replied OK:" reply)
                (swap! request-number inc)
                (recur REQUEST_RETRIES socket poller))
              (do
                (println "Malformed reply from server:" reply)
                (recur ))))))

      (zmq/polling
        {:receive-opts {:stringify true}}
        [socket :pollin [[reply]]
         (if (= (str @request-number reply))
           (do
             (println "Server replied OK:" reply)
             (reset! waiting? false))
           (println "Malformed reply from server:" reply))]
        (while (and @waiting? (zmq/polling?))
          (let [got-msgs (zmq/poll {:timeout REQUEST_TIMEOUT})]
            (if (contains? got-msgs socket)
              )))))))
