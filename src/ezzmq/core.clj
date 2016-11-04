(ns ezzmq.core
  (:require [potemkin.namespaces :refer (import-vars)]))

(defn- import-all-vars
  "Imports all public vars from a namespace into the ezzmq.core namespace."
  [ns]
  (eval (list `import-vars (cons ns (keys (ns-publics ns))))))

(def ^:private namespaces
  '[ezzmq.context
    ezzmq.socket
    ezzmq.message
    ezzmq.thread])

(doseq [ns namespaces]
  (require ns)
  (import-all-vars ns))

