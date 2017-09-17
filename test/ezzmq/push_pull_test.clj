(ns ezzmq.push-pull-test
  (:require [clojure.test    :refer :all]
            [ezzmq.core      :as    zmq]
            [ezzmq.test-util :as    util]))

(def ^:dynamic *port* nil)

(defn run-server
  []
  (let [socket (zmq/socket :push {:bind (format "tcp://*:%s" *port*)})]
    ; give client time to connect so it gets all the messages
    (Thread/sleep 1000)
    (doseq [n (range 100)]
      ; (println (format "SERVER: Sending msg: %s" n))
      (zmq/send-msg socket (str n)))))

(use-fixtures :once
  (fn [run-tests]
    (alter-var-root #'*port* (constantly (util/find-open-port)))
    (util/for-each-context-type
      (zmq/with-new-context
        (util/future (run-server))
        (run-tests)))))

(deftest push-pull-tests
  (testing "a PULL client"
    (let [pull (zmq/socket :pull {:connect (format "tcp://*:%s" *port*)})]
      (testing "can connect to a PUSH server"
        (is (= org.zeromq.ZMQ$Socket (type pull))))
      (testing "can receive messages from a PUSH server"
        (let [msgs (atom #{})]
          (doseq [_ (range 100)]
            (let [[msg] (zmq/receive-msg pull {:stringify true})]
              ; (println "CLIENT: Received msg:" msg)
              (swap! msgs conj (Integer/parseInt msg))))
          (is (= (set (range 100)) @msgs)))))))
