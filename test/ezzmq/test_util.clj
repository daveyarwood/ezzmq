(ns ezzmq.test-util
  (:import [java.net ServerSocket]))

(defn find-open-port
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))
