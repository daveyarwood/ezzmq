(ns ezzmq.test-util
  (:require [ezzmq.core :as zmq])
  (:import [java.net ServerSocket])
  (:refer-clojure :exclude [future]))

(defn find-open-port
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defmacro for-each-context-type
  "Used for running tests using both ZContext and ZMQ.Context."
  [& body]
  `(do
     (binding [zmq/*context-type* :zcontext]
       (println "• Running tests using ZContext...")
       ~@body)
     (binding [zmq/*context-type* :zmq.context]
       (println "• Running tests using ZMQ.Context...")
       ~@body)))

(defmacro future
  "clojure.core/future, but if an exception gets thrown it is printed so we can
   see it."
  [& body]
  `(clojure.core/future
     (try
       ~@body
       (catch Throwable e#
         (println "Error in future:" (.getMessage e#))
         (.printStackTrace e#)
         (throw e#)))))
