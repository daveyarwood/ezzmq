(ns ezzmq.message-test
  (:require [clojure.test    :refer :all]
            [ezzmq.core      :as     zmq]
            [ezzmq.test-util :as     util])
  (:import [org.zeromq ZMQ$Error ZMQException]))

(use-fixtures :once
  (fn [run-tests]
    (util/for-each-context-type
      (zmq/with-new-context
        (run-tests)))))

(def ^:dynamic *port* nil)

(def ^:const test-msg ["TEST" "MESSAGE"])

(defn run-responsive-server
  "The server starts, receives a message, immediately responds, and shuts down."
  []
  (let [socket (zmq/socket :rep {:bind (format "tcp://*:%s" *port*)})]
    (zmq/receive-msg socket)
    (zmq/send-msg socket test-msg)))

(defn run-laggy-server
  "The server starts, receives a message, waits 1000 ms, then responds, and
   shuts down."
  []
  (let [socket (zmq/socket :rep {:bind (format "tcp://*:%s" *port*)})]
    (zmq/receive-msg socket)
    (Thread/sleep 1000)
    ;; The tests stop running before this last message is sent, causing an
    ;; ETERM exception because the context has been terminated. This is
    ;; expected, so we can catch and swallow it.
    (try
      (zmq/send-msg socket test-msg)
      (catch ZMQException e
        (let [ETERM (.getCode ZMQ$Error/ETERM)]
          (when-not (= ETERM (.getErrorCode e))
            (throw e)))))))

(deftest if-msg-tests
  (testing "if-msg"
    (testing "executes the 'then' branch if a message is received"
      (binding [*port* (util/find-open-port)]
        (util/future (run-responsive-server))
        ;; Give the server time to start.
        (Thread/sleep 500)
        (let [socket      (zmq/socket :req {:connect (format "tcp://*:%s" *port*)})
              then-result (atom nil)
              else-result (atom nil)]
          (zmq/send-msg socket "hello")
          (zmq/if-msg socket {:stringify true :timeout 100} [msg]
            (reset! then-result msg)
            (reset! else-result :no-msg))
          (is (= test-msg @then-result))
          (is (nil? @else-result)))))
    (testing "executes the 'else' branch if a message is NOT received"
      (binding [*port* (util/find-open-port)]
        (util/future (run-laggy-server))
        ;; Give the server time to start.
        (Thread/sleep 500)
        (let [socket      (zmq/socket :req {:connect (format "tcp://*:%s" *port*)})
              then-result (atom nil)
              else-result (atom nil)]
          (zmq/send-msg socket "hello")
          (zmq/if-msg socket {:stringify true :timeout 100} [msg]
            (reset! then-result msg)
            (reset! else-result :no-msg))
          (is (= nil @then-result))
          (is (= :no-msg @else-result)))))))

(deftest when-msg-tests
  (testing "when-msg"
    (testing "executes the 'then' branch if a message is received"
      (binding [*port* (util/find-open-port)]
        (util/future (run-responsive-server))
        ;; Give the server time to start.
        (Thread/sleep 500)
        (let [socket      (zmq/socket :req {:connect (format "tcp://*:%s" *port*)})
              then-result (atom nil)]
          (zmq/send-msg socket "hello")
          (zmq/when-msg socket {:stringify true :timeout 100} [msg]
            (reset! then-result msg))
          (is (= test-msg @then-result)))))
    (testing "executes the 'else' branch if a message is NOT received"
      (binding [*port* (util/find-open-port)]
        (util/future (run-laggy-server))
        ;; Give the server time to start.
        (Thread/sleep 500)
        (let [socket      (zmq/socket :req {:connect (format "tcp://*:%s" *port*)})
              then-result (atom nil)]
          (zmq/send-msg socket "hello")
          (zmq/when-msg socket {:stringify true :timeout 100} [msg]
            (reset! then-result msg))
          (is (= nil @then-result)))))))

