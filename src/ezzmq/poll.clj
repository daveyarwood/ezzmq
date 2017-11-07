(ns ezzmq.poll
  (:require [ezzmq.context :refer (*context* before-shutdown)]
            [ezzmq.message :refer (receive-msg)])
  (:import [java.nio.channels ClosedChannelException]
           [org.zeromq ZMQ$Context ZContext ZMQ$Poller]
           [zmq ZError$IOException]))

(defprotocol PollerMaker
  (create-poller [ctx num-items]))

(extend-protocol PollerMaker
  ZContext
  (create-poller [ctx num-items] (.createPoller ctx num-items))

  ZMQ$Context
  (create-poller [ctx num-items] (.poller ctx num-items)))

(def ^:const poll-types
  {:pollin  ZMQ$Poller/POLLIN
   :pollout ZMQ$Poller/POLLOUT
   :pollerr ZMQ$Poller/POLLERR})

(defn poller
  "Convenience function for creating a poller and registering poll items.

   A poll item is expressed as a 2 or 3 tuple consisting of:

     1. the socket to poll (org.zeromq.ZMQ$Socket)
     2. the poll type as a keyword (:pollin, :pollout, or :pollerr)
     3. (optional) a callback function to call when `poll` is called on the
        poller and the poll item is in a \"positive\" state, i.e. for :pollin,
        there is a message available to receive, for :pollout, the socket is
        writable, and for :pollerr, the socket is in an error state.

        Callback functions for :pollin items take a single argument, the
        message received, which is a vector of frames.

   When the callback function is omitted, no action is taken when the socket is
   in a positive state. This might be useful if you ever need to bypass the
   callback machinery and handle socket events yourself in some custom way.

   `send-opts` and `receive-opts` are passed along to the `send-msg` and
   `receive-msg` functions. For example, your options can include
   `:receive-opts {:stringify true}` if you don't want to manually coerce
   message frames to strings within your callback functions.

   Returns a map with the following values:
     - :poller - the org.zeromq.ZMQ$Poller object
     - :items - a seq of maps representing the poll items
     - :channel-open? - an atom tracking whether the poller is able to poll.
       This value is initially true, but becomes false if the poller ever ends
       up in a state where it can't poll anymore.

   Example usage:

     (let [backend     (zmq/socket :dealer {:bind \"tcp://*:12345\"})
           frontend    (zmq/socket :router {:bind \"tcp://*:12346\"})
           backend-fn  (fn [msg] (zmq/send-msg frontend msg))
           frontend-fn (fn [msg] (zmq/send-msg backend msg))
           poller      (zmq/poller [[backend :pollin backend-fn]
                                    [frontend :pollin frontend-fn]])]
       (while (zmq/polling? poller)
         (zmq/poll poller)))

   See also: the `polling` macro for a higher-level API."
  [poll-items & [{:keys [send-opts receive-opts] :as opts}]]
  (let [items
        (for [[[socket poll-type & [callback-fn]] index]
              (map vector poll-items (range))]
          {:index     index
           :socket    socket
           :poll-type poll-type
           :handler   (if callback-fn
                        (if (= :pollin poll-type)
                          #(callback-fn (receive-msg socket receive-opts))
                          callback-fn)
                        (fn []))})

        poller
        (create-poller *context* (count items))

        channel-open?
        (atom true)]
    (doseq [{:keys [socket poll-type]} items]
      (.register poller socket (get poll-types poll-type)))
    (before-shutdown (reset! channel-open? false))
    {:poller poller
     :items items
     :channel-open? channel-open?}))

(def ^:dynamic *poller* nil)
(def ^:dynamic *poll-items* nil)
(def ^:dynamic *channel-open?* nil)

(defmacro polling
  "Creates a scope for polling.

   An example is worth a thousand words:

   (let [backend  (zmq/socket :dealer {:bind \"tcp://*:12345\"})
         frontend (zmq/socket :router {:bind \"tcp://*:12346\"})]
     (zmq/polling {}
       [backend  :pollin [msg] (zmq/send-msg frontend msg)
        frontend :pollin [msg] (zmq/send-msg backend msg)]
       (zmq/while-polling
         (zmq/poll))))"
  [opts bindings & body]
  (let [items (vec (for [[socket poll-type bindings body]
                         (partition-all 4 bindings)]
                     [socket poll-type `(fn ~bindings ~body)]))]
    `(let [poller# (poller ~items ~opts)]
       (binding [*poller*       (:poller poller#)
                 *poll-items*   (:items poller#)
                 *channel-open?* (:channel-open? poller#)]
         ~@body))))

(defn polling?
  "Returns true if the current thread is not interrupted and the channel to be
   polled is still open for polling."
  ([]
   (polling? {:channel-open? *channel-open?*}))
  ([{:keys [channel-open?]}]
   (and (not (.. Thread currentThread isInterrupted))
        @channel-open?)))

(defmacro while-polling
  "Polls for messages as long as the channel is open and the current thread is
   not interrupted."
  [& body]
  `(while (polling?)
     ~@body))

(defn poll
  "Polls each poll item in the poller and takes action if appropriate.

	 There are two ways to provide a poller:

	 - The high-level way is to use the `polling` macro to create a scope. See the
     docstring for `polling` for an example.

	 - The low-level way is to create one via the `poller` function and pass it in
     as a second argument to this function. See the docstring for `poller` for
     more information and an example.

   Returns the set of sockets on which action was taken, e.g. a message was
   received and handled."
  ([]
   (poll {}))
  ([opts]
   (poll opts {:poller        *poller*
               :items         *poll-items*
               :channel-open? *channel-open?*}))
  ([{:keys [timeout]} {:keys [poller items channel-open?]}]
   (letfn [(abort [timeout]
             (reset! channel-open? false)
             (when timeout (Thread/sleep timeout))
             #{})]
     (if-not @channel-open?
       (abort timeout)
       (let [poll-result (if timeout
                           (.poll poller timeout)
                           (.poll poller))]
         (if (= -1 poll-result)
           (abort nil)
           (let [got-msgs (atom #{})]
             (doseq [{:keys [poll-type index handler socket]} items]
               (when (case poll-type
                       :pollin  (.pollin  poller index)
                       :pollout (.pollout poller index)
                       :pollerr (.pollerr poller index))
                 (swap! got-msgs conj socket)
                 (handler)))
             @got-msgs)))))))
