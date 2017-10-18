(ns ezzmq.socket-options-test
  (:require [clojure.test    :refer :all]
            [ezzmq.core      :as     zmq]
            [ezzmq.test-util :as     util])
  (:import [org.zeromq ZMQ$Socket]))

(use-fixtures :once
              (fn [run-tests]
                (util/for-each-context-type
                  (run-tests))))

(def ^:dynamic *port* nil)

(def socket-types
  [[:pair   ]
   [:pub    ]
   [:sub    {:linger 0}]
   [:req    ]
   [:rep    ]
   [:xreq   ]
   [:xrep   ]
   [:dealer ]
   [:router ]
   [:xpub   ]
   [:xsub   {:linger 0}]
   [:pull   ]
   [:push   ]])

(def options
  [["send HWM"    :send-hwm    #(.getSndHWM %)]
   ["receive HWM" :receive-hwm #(.getRcvHWM %)]
   ["linger"      :linger      #(.getLinger %)]])

(deftest socket-options-tests
  (doseq [[socket-type {:keys [send-hwm receive-hwm linger]
                        :as defaults
                        :or {send-hwm    1000
                             receive-hwm 1000
                             linger      -1}}] socket-types
          action [:bind :connect]
          protocol ["tcp" "inproc"]
          :let [port (util/find-open-port)
                make-socket (fn [& [opts]]
                              (let [port (util/find-open-port)
                                    address (case protocol
                                              "tcp"    (str "tcp://*:" port)
                                              "inproc" (str "inproc://" port))]
                                (zmq/socket socket-type
                                            (merge {action address} opts))))]]
    (testing (format "%s / %s ::::" socket-type action)
      (zmq/with-new-context
        (let [socket (make-socket)]
          (testing "can create a socket with no options besides bind/connect"
            (is (= ZMQ$Socket (type socket))))
          (testing "default value of"
            (testing "send HWM"
              (is (= send-hwm (.getSndHWM socket))))
            (testing "receive HWM"
              (is (= receive-hwm (.getRcvHWM socket))))
            (testing "linger"
              (is (= linger (.getLinger socket)))))))
      (doseq [[option-name option-key get-fn] options]
        (zmq/with-new-context
          (let [socket (make-socket {option-key 42})]
            (testing (str "can create a socket with a desired "
                          option-name
                          " value")
              (is (= ZMQ$Socket (type socket))))
            (testing (str option-key "option correctly sets " option-name)
              (is (= 42 (get-fn socket))))))))))

