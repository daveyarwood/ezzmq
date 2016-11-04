(ns ezzmq.thread
  (:import [org.zeromq ZMQ$Error ZMQException]))

(defmacro worker-thread
  [{:keys [on-interrupt] :as opts} & body]
  `(future
     (try
       ~@body
       (catch ZMQException e#
         (let [ETERM# (.getCode ZMQ$Error/ETERM)]
           (if (= ETERM# (.getErrorCode e#))
             ((or ~on-interrupt #()))
             (throw e#)))))))

