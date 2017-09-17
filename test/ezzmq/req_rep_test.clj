(ns ezzmq.req-rep-test
  (:require [clojure.test    :refer :all]
            [ezzmq.core      :as     zmq]
            [ezzmq.test-util :as     util]))

(def ^:dynamic *port* nil)

(defn run-server
  []
  (let [socket (zmq/socket :rep {:bind (format "tcp://*:%s" *port*)})]
    (dotimes [n 5]
      (let [msg (zmq/receive-msg socket {:stringify true})]
        ; (println (format "SERVER: Received msg: %s" msg))
        (zmq/send-msg socket (format "Hello #%s from server" (inc n)))))
    (dotimes [n 3]
      (let [msg (zmq/receive-msg socket {:stringify true})]
        ; (println (format "SERVER: Received msg: %s" msg))
        (zmq/send-msg socket msg)))))

(use-fixtures :once
  (fn [run-tests]
    (alter-var-root #'*port* (constantly (util/find-open-port)))
    (util/for-each-context-type
      (zmq/with-new-context
        (util/future (run-server))
        (run-tests)))))

(deftest req-rep-tests
  (testing "a REQ client"
    (let [socket (zmq/socket :req {:connect (format "tcp://*:%s" *port*)})]
      (testing "can connect to a REP server"
        (is (= org.zeromq.ZMQ$Socket (type socket))))
      (testing "can send and receive messages to/from a REP server"
        (dotimes [n 5]
          (zmq/send-msg socket (format "Hello #%s from client" (inc n)))
          (let [msg (zmq/receive-msg socket {:stringify true})]
            ; (println (format "CLIENT: Received msg: %s" msg))
            (is (= [(format "Hello #%s from server" (inc n))] msg)))))
      (testing "can send and receive multi-part messages"
        (doseq [req [["just" "strings"]
                     (map #(.getBytes %) ["only" "byte" "arrays"])
                     (concat ["some" "strings" "and"]
                             (map #(.getBytes %) ["some" "byte" "arrays"]))]]
          (zmq/send-msg socket req)
          (let [res (zmq/receive-msg socket {:stringify true})]
            ; (println (format "CLIENT: Received msg: %s" res))
            (is (= res (map #(String. %) req)))))))))

