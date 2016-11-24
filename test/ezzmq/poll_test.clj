(ns ezzmq.poll-test
  (:require [clojure.test    :refer :all]
            [ezzmq.core      :as    zmq]
            [ezzmq.test-util :as    util]))

(def ^:dynamic *pub-port* nil)
(def ^:dynamic *push-port* nil)

(defn run-pub-server
  []
  (let [socket (zmq/socket :pub {:bind (format "tcp://*:%s" *pub-port*)})]
    ; give client time to connect so it gets all the messages
    (Thread/sleep 1000)
    (doseq [n (range 0 100 2)] ; 0 2 4 6 8 10 ... 94 96 98
      ; (println (format "SERVER: Sending msg: %s" n))
      (zmq/send-msg socket (str n)))))

(defn run-push-server
  []
  (let [socket (zmq/socket :push {:bind (format "tcp://*:%s" *push-port*)})]
    ; give client time to connect so it gets all the messages
    (Thread/sleep 1000)
    (doseq [n (range 1 100 2)] ; 1 3 5 7 9 11 ... 95 97 99
      ; (println (format "SERVER: Sending msg: %s" n))
      (zmq/send-msg socket (str n)))))

(use-fixtures :once
  (fn [run-tests]
    (alter-var-root #'*pub-port*  (constantly (util/find-open-port)))
    (alter-var-root #'*push-port* (constantly (util/find-open-port)))
    (util/for-each-context-type
      (zmq/with-new-context
        (future (run-pub-server))
        (future (run-push-server))
        (run-tests)))))

(deftest poll-tests
  (testing "a client"
    (let [sub  (zmq/socket :sub  {:connect (format "tcp://*:%s" *pub-port*)})
          pull (zmq/socket :pull {:connect (format "tcp://*:%s" *push-port*)})]
      (testing "can connect to two sockets"
        (is (= org.zeromq.ZMQ$Socket (type sub)))
        (is (= org.zeromq.ZMQ$Socket (type pull))))
      (testing "can poll two sockets"
        (let [msgs  (atom #{})
              tally (atom {0 0, 1 0})]
          (zmq/polling {:stringify true}
            [sub :pollin [msg]
             (do
               ; (println "CLIENT: Received msg:" msg)
               (swap! msgs conj (Integer/parseInt (first msg))))

             pull :pollin [msg]
             (do
               ; (println "CLIENT: Received msg:" msg)
               (swap! msgs conj (Integer/parseInt (first msg))))]
            (while (< (count @msgs) 100)
              (let [got-msgs (zmq/poll)]
                (swap! tally #(reduce (fn [tally socket-index]
                                        (update tally socket-index inc))
                                      %
                                      got-msgs)))))
          (testing "and receive all the messages"
            (is (= (set (range 100)) @msgs)))
          (testing "and on each poll, the `poll` call returns a set of indexes
                    representing sockets on which messages were received"
            (is (= {0 50, 1 50} @tally))))))))
