(ns ezzmq.util)

(defn errfmt
  [msg & fmt-args]
  (throw (Exception. (apply format msg fmt-args))))

(defn as-byte-array
  [x]
  (condp = (class x)
    (Class/forName "[B")
    x

    String
    (.getBytes x)

    (errfmt "Can't convert %s (%s) into a byte array." x (class x))))

