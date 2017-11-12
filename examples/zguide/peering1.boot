#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.8.1"]])

(require '[ezzmq.core :as zmq])

(defn endpoint-for
  [id]
  (format "ipc://%s-state.ipc" id))

(defn -main
  [self & peers]
  (zmq/with-new-context
    (let [state-backend  (zmq/socket :pub {:bind (endpoint-for self)})
          state-frontend (zmq/socket :sub {:connect (map endpoint-for peers)})]
      (zmq/polling
        {:receive-opts {:stringify true}}
        [state-frontend :pollin [[peer-name workers-available]]
         (println (format "%s - %s workers available"
                          peer-name workers-available))]
        (zmq/while-polling
          (let [got-msgs (zmq/poll {:timeout 1000})]
            (when (empty? got-msgs)
              (zmq/send-msg state-backend [self (str (rand-int 10))]))))))))

