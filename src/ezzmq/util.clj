(ns ezzmq.util)

(defn errfmt
  [msg & fmt-args]
  (throw (Exception. (apply format msg fmt-args))))

