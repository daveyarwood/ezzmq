(ns ezzmq.pub-sub-test
  (:require [clojure.test    :refer :all]
            [ezzmq.core      :as    zmq]
            [ezzmq.test-util :as    util]))

(def ^:dynamic *port* nil)

(defn run-server
  []
  (let [socket (zmq/socket :pub {:bind (format "tcp://*:%s" *port*)})
        msgs   (for [topic ["A" "B" "C" "D" "E"]
                     msg   ["one" "two" "three" "four"]]
                 (format "%s %s" topic msg))]
    ; give client time to connect so it gets all the test messages
    (Thread/sleep 1000)
    (doseq [msg msgs]
      ; (println (format "SERVER: Sending msg: %s" msg))
      (zmq/send-msg socket msg))))

(use-fixtures :once
  (fn [run-tests]
    (alter-var-root #'*port* (constantly (util/find-open-port)))
    (util/for-each-context-type
      (zmq/with-new-context
        (future (run-server))
        (run-tests)))))

(deftest pub-sub-tests
  (testing "a SUB client"
    (let [socket (zmq/socket :sub {:connect (format "tcp://*:%s" *port*)
                                   :subscribe "C"})]
      (testing "can connect to a PUB server and subscribe to a topic"
        (is (= org.zeromq.ZMQ$Socket (type socket))))
      (testing "can receive relevant messages from a PUB server"
        (doseq [expected ["C one" "C two" "C three" "C four"]]
          (let [msg (zmq/receive-msg socket :stringify true)]
            ; (println (format "CLIENT: Received msg: %s" msg))
            (is (= expected (first msg)))))))))
