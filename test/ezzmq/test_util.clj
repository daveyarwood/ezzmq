(ns ezzmq.test-util
  (:require [ezzmq.core :as zmq])
  (:import [java.net ServerSocket]))

(defn find-open-port
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defmacro for-each-context-type
  "Used for running tests using both ZContext and ZMQ.Context."
  [& body]
  `(do
     (binding [zmq/*context-type* :zcontext]
       (println)
       (println "Running tests using ZContext...")
       ~@body)
     (binding [zmq/*context-type* :zmq.context]
       (println)
       (println "Running tests using ZMQ.Context...")
       ~@body)))
