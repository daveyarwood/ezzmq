(ns ezzmq.timeout-test
  (:require [clojure.test    :refer :all]
            [ezzmq.core      :as     zmq]
            [ezzmq.test-util :as     util]))

(def ^:dynamic *port* nil)

(use-fixtures :once
  (fn [run-tests]
    (alter-var-root #'*port* (constantly (util/find-open-port)))
    (util/for-each-context-type
      (zmq/with-new-context
        (run-tests)))))

(deftest timeout-tests
  (testing "send timeout"
    (let [socket (zmq/socket :req {:connect (format "tcp://*:%s" *port*)})]
      (testing "a socket's send timeout is initially -1 (block indefinitely)"
        (is (= -1 (.getSendTimeOut socket))))
      (testing "with-send-timeout sets the send timeout when sending a message"
        (zmq/with-send-timeout socket 1000
          (is (= 1000 (.getSendTimeOut socket))))
        (testing "and afterwards, the previous value is restored"
          (is (= -1 (.getSendTimeOut socket)))))))
  (testing "receive timeout"
    (let [socket (zmq/socket :rep {:bind (format "tcp://*:%s" *port*)})]
      (testing "a socket's receive timeout is initially -1 (block indefinitely)"
        (is (= -1 (.getReceiveTimeOut socket))))
      (testing "with-receive-timeout sets the receive timeout when receiving a
                message"
        (zmq/with-receive-timeout socket 1000
          (is (= 1000 (.getReceiveTimeOut socket)))
          (testing "and the receive timeout determines how long to block while
                      waiting for the message to be sent"
              (let [start (System/currentTimeMillis)
                    _     (zmq/receive-msg socket)
                    end   (System/currentTimeMillis)]
                (is (>= (- end start) 1000)))))
        (testing "and afterwards, the previous value is restored"
          (is (= -1 (.getReceiveTimeOut socket)))))
      (testing "the receive-msg :timeout option sets the receive timeout when
                  receiving a message"
          (is (= -1 (.getReceiveTimeOut socket)))
          (let [start (System/currentTimeMillis)
                _     (zmq/receive-msg socket {:timeout 1000})
                end   (System/currentTimeMillis)]
            (is (>= (- end start) 1000)))
          (let [start (System/currentTimeMillis)
                _     (zmq/receive-msg socket {:timeout 0})
                end   (System/currentTimeMillis)]
            (is (< (- end start) 500)))
          (is (= -1 (.getReceiveTimeOut socket)))))))

