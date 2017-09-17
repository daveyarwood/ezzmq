(ns ezzmq.poll
  (:require [ezzmq.context :refer (*context* before-shutdown)]
            [ezzmq.message :refer (receive-msg)])
  (:import [java.nio.channels ClosedChannelException]
           [org.zeromq ZMQ$Context ZContext ZMQ$Poller]
           [zmq ZError$IOException]))

(defprotocol PollerMaker
  (create-poller [ctx socket-type]))

(extend-protocol PollerMaker
  ZContext
  (create-poller [ctx socket-type] (.createPoller ctx socket-type))

  ZMQ$Context
  (create-poller [ctx socket-type] (.poller ctx socket-type)))

(def ^:const poll-types
  {:pollin  ZMQ$Poller/POLLIN
   :pollout ZMQ$Poller/POLLOUT
   :pollerr ZMQ$Poller/POLLERR})

(def ^:dynamic *poller* nil)
(def ^:dynamic *poll-items* nil)
(def ^:dynamic *channel-open* nil)

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
                                                    {:stringify
                                                     (:stringify ~opts)})]
                                     ~item-body)))
                              ~@item-body)}))]
    `(let [poll-items# ~items
           poller#     (create-poller *context* (count poll-items#))]
       (binding [*poller*       poller#
                 *poll-items*   poll-items#
                 *channel-open* (atom true)]
         (let [channel-open# *channel-open*]
           (before-shutdown (reset! channel-open# false)))
         (doseq [poll-item# poll-items#]
           (.register poller#
                      (:socket poll-item#)
                      (get poll-types (:type poll-item#))))
         ~@body))))

(defn polling?
  "Returns true if the current thread is not interrupted and the channel to be
   polled is still open for polling."
  []
  (and (not (.. Thread currentThread isInterrupted))
       @*channel-open*))

(defmacro while-polling
  "Polls for messages as long as the channel is open and the current thread is
   not interrupted."
  [& body]
  `(while (polling?)
     ~@body))

(defn poll
  "Poll for messages on each sockets in *poll-items*.

   Returns a set of indexes representing the sockets on which messages were
   received."
  [& [timeout]]
  (letfn [(abort [timeout]
            (reset! *channel-open* false)
            (when timeout (Thread/sleep timeout))
            #{})]
    (if-not @*channel-open*
      (abort timeout)
      (let [poll-result (if timeout
                          (.poll *poller* timeout)
                          (.poll *poller*))]
        (if (= -1 poll-result)
          (abort nil)
          (let [got-msgs (atom #{})]
            (doseq [{:keys [type index handler]} *poll-items*]
              (when (case type
                      :pollin  (.pollin  *poller* index)
                      :pollout (.pollout *poller* index)
                      :pollerr (.pollerr *poller* index))
                (swap! got-msgs conj index)
                (handler)))
            @got-msgs))))))
