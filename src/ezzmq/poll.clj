(ns ezzmq.poll
  (:require [ezzmq.context :refer (before-shutdown)]
            [ezzmq.message :refer (receive-msg)])
  (:import [org.zeromq ZMQ$Poller]))

(def ^:const poll-types
  {:pollin  ZMQ$Poller/POLLIN
   :pollout ZMQ$Poller/POLLOUT
   :pollerr ZMQ$Poller/POLLERR})

(def ^:dynamic *poller* nil)

(def ^:dynamic *poll-items* nil)

(defmacro polling
  [opts bindings & body]
  (let [items (vec
                (for [[[socket type item-bindings item-body] index]
                      (map vector (partition-all 4 bindings) (range))]
                  {:index   index
                   :socket  socket
                   :type    type
                   :handler (if (= :pollin type)
                              (let [[msg-var] item-bindings]
                                `(fn []
                                   (let [~msg-var (receive-msg
                                                    ~socket
                                                    :stringify
                                                    (:stringify ~opts))]
                                     ~item-body)))
                              ~@item-body)}))]
    `(let [poll-items# ~items
           poller#     (ZMQ$Poller. (count poll-items#))]
       (binding [*poller*     poller#
                 *poll-items* poll-items#]
         (doseq [poll-item# poll-items#]
           (.register poller#
                      (:socket poll-item#)
                      (get poll-types (:type poll-item#))))
         ~@body))))

(comment
  "As of JeroMQ 0.3.6, there are known issues with polling selector resources
   not being deallocated / perhaps some race condition where the selector still
   tries to poll even after the channel has been closed.

   There are a number of open JeroMQ issues related to this, and I have filed
   one in particular re: java.nio.channels.ClosedChannelException:

   https://github.com/zeromq/jeromq/issues/380

   The bug here is that if happen to be polling at the time that the context is
   terminated, the poll selector is not closed properly and a
   ClosedChannelException is thrown.

   Until this bug is fixed in a release of JeroMQ and we update ezzmq to use the
   new version of JeroMQ, we have to be careful to stop polling before the
   context is terminated.

   See the `before-shutdown` hook in examples/mspoller.boot for an example of
   how to do this.")

(defn poll
  "Poll for messages on each sockets in *poll-items*.

   Returns a set of indexes representing the sockets on which messages were
   received."
  [& [timeout]]
  (if timeout (.poll *poller* timeout) (.poll *poller*))
  (let [got-msgs (atom #{})]
    (doseq [{:keys [type index handler]} *poll-items*]
      (when (case type
              :pollin  (.pollin  *poller* index)
              :pollout (.pollout *poller* index)
              :pollerr (.pollerr *poller* index))
        (do
          (swap! got-msgs conj index)
          (handler))))
    @got-msgs))
