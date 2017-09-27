#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.0"]])

(require '[ezzmq.core :as zmq])

(defn -main
  ([]
   (println "No port specified.")
   (System/exit 1))
  ([port & [zip-code]]
   (zmq/with-new-context
     (let [zip-code (or zip-code "10001")
           socket (zmq/socket :sub {:connect   (str "tcp://*:" port)
                                    :subscribe zip-code})]
       (println (format "Receiving updates for zip code %s..." zip-code))
       (loop [n 0 temperature-sum 0]
         (if (< n 25)
           (let [msg (zmq/receive-msg socket {:stringify true})]
             (println "Received weather update:" msg)
             (let [temp (->> msg
                             first
                             (re-find #"\d{5} (-?\d+) \d+")
                             second
                             Integer/parseInt)]
               (recur (inc n) (+ temperature-sum temp))))
           (println (format "The average temperature for zip code %s is %s"
                            zip-code
                            (/ temperature-sum 25.0)))))))))
