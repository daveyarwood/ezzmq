#!/usr/bin/env boot

(set-env! :dependencies '[[io.djy/ezzmq "0.7.2"]])

(require '[ezzmq.core :as zmq])

(defn spawn-step-1
  []
  (zmq/worker-thread {}
    (let [step-2 (zmq/socket :pair {:connect "inproc://step2"})]
      (Thread/sleep 500) ; simulate work
      (zmq/send-msg step-2 "Step 1 ready, signaling step 2."))))

(defn spawn-step-2
  []
  (zmq/worker-thread {}
    (let [step-2 (zmq/socket :pair {:bind "inproc://step2"})
          step-3 (zmq/socket :pair {:connect "inproc://step3"})]
      (spawn-step-1)
      (println (zmq/receive-msg step-2 {:stringify true}))
      (Thread/sleep 500) ; simulate work
      (zmq/send-msg step-3 "Step 2 ready, signaling step 3."))))

(defn -main
  []
  (zmq/with-new-context
    (let [step-3 (zmq/socket :pair {:bind "inproc://step3"})]
      (spawn-step-2)
      (println (zmq/receive-msg step-3 {:stringify true}))
      (Thread/sleep 500) ; simulate work
      (println "Step 3: Success!"))))
