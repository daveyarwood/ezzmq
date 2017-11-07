# CHANGELOG

## 0.8.0 (2017-11-05)

* For greater flexibility when using pollers, some of the implementation of the
  ezzmq polling code has been refactored into a lower-level API:

  * `zmq/poller` creates a poller, registers poll items, and stores poll item
    callbacks and an atom tracking whether the poller is still able to poll.

  * `zmq/polling?` now takes an optional `zmq/poller` argument, and returns true
    if the current thread hasn't been interrupted and that poller is still able
    to poll.

  * `zmq/poll` now takes an optional `zmq/poller` argument, and polls that
    poller specifically.

  These lower-level polling functions make it possible to write ezzmq code
  involving multiple pollers, e.g. for complex setups where you only want to
  poll certain sockets each time through a loop, depending on certain conditions
  that can change over time. See the [`lbbroker` example][lbbroker] for an
  example of the low-level API in action.

* Refactored the existing (higher-level) polling API to use the lower-level
  polling code.

  Existing polling code will still work without changes, with the exception of
  two small breaking changes (see below).

* **BREAKING CHANGE**: The `zmq/polling` macro options map now has two top-level
  keys, `send-opts` and `receive-opts`, which are passed on to the `send-msg`
  and `receive-msg` functions internally. Before, this was just a flat map of
  options, only one of which was supported: `stringify` -- the value was passed
  on to the `receive-msg` function. So, any code looking like this:

  ```clojure
  (zmq/polling {:stringify true} ...)
  ```

  ...must be rewritten to look like this:

  ```clojure
  (zmq/polling {:receive-opts {:stringify true}} ...)
  ```

* **BREAKING CHANGE**: The first argument to `zmq/poll` is now a proper options map, instead of an optional `timeout` argument. So, any code looking like this:

	```clojure
	(zmq/poll 1000)
	```

	...must be rewritten to look like this:

	```clojure
	(zmq/poll {:timeout 1000})
	```

## 0.7.2 (2017-10-29)

* Added support for setting socket identity via a new socket option: `:identity`

  Example usage:

  ```clojure
  (zmq/socket :req {:identity "dave" :connect "inproc://somewhere"})
  ```

## 0.7.1 (2017-10-18)

* Added support for conveniently setting several options on a socket upon
  creating it:

  * `:send-hwm`
  * `:receive-hwm`
  * `:linger`

  There are a lot more ZeroMQ socket options that we could support, and we can
  add them as needed. PRs welcome!

  Example usage:

  ```clojure
  (zmq/socket :pub {:bind "tcp://*:12345" :send-hwm 10000})
  ```

## 0.7.0 (2017-09-16)

* **BREAKING CHANGE**: `send-msg` and `receive-msg` now take an explicit options
  map instead of inline keyword arguments.

* Added a `:timeout` option to both `send-msg` and `receive-msg`. When used,
  the socket temporarily gets its send/receive timeout set to the provided
  number of milliseconds. After the send/receive completes or times out, the
  original timeout value of the socket is restored.

* `with-send-timeout` and `with-receive-timeout` macros provide a scope where
  the send/receive timeout has a particular value. (These macros are used under
  the hood in `send-msg` and `receive-msg` to implement the `:timeout` option.)

* Added `if-msg` and `when-msg` helper macros for doing things conditionally
  depending on whether or not a message was received within a timeout.

  For example:

  ```clojure
  (zmq/if-msg socket {:stringify true} [msg]
    (println "Got message:" msg)
    (println "Didn't get a message."))

  (zmq/when-msg socket {:stringify true} [msg]
    (println "Got a message!")
    (println "Here it is:")
    (prn msg))
  ```

* `poll` now returns the set of sockets that received messages, instead of the
  set of indexes, which are less meaningful.

## 0.6.0 (2017-09-14)

* Updated JeroMQ dependency to 0.4.2. I'm bumping the minor version of ezzmq
  because JeroMQ 0.4.1 brought significant changes, including that JeroMQ is now
  based on libzmq 4.1.7 (ZMTP/3.0) instead of libzmq 3.2.5 (ZMTP/2.0). In theory
  this was not a breaking change, but I feel it is significant and worth bumping
  the minor version of ezzmq.

## 0.5.3 (2017-03-25)

* Updated JeroMQ dependency to 0.4.0.

* ezzmq pollers are now initialized via the context, and thanks to upstream
  changes in JeroMQ, the context ensures that poll selector resources are
  properly released when the context is terminated.

* Thanks to upstream changes in JeroMQ, we no longer have to catch
  ClosedChannelExceptions in ezzmq, so the try/catch in ezzmq has been removed.

## 0.5.1 (2016-11-30)

* Exposed a `polling?` function used in the implementation of `while-polling`.
  This allows you to define your own end conditions for polling, building upon
  the default behavior that `while-polling` gives you.

  See the README for more info.

## 0.5.0 (2016-11-30)

* Updated JeroMQ dependency to 0.3.6.

* Added some code that guards against context before- and after-shutdown hooks running more than once.

* Improvements to polling reliability -- see the bullet points below.

  tl;dr: do this now and you'll run into less weird issues when polling:

  ```clojure
  (require '[ezzmq.core :as zmq])

  (zmq/with-new-context
    (let [socket1 ...
          socket2 ...]
      (zmq/polling {}
        [socket1 :pollin [msg] (handle msg)
         socket2 :pollin [msg] (handle msg)]
        (zmq/while-polling
          (zmq/poll 1000)))))
  ```

  * Added an internal `*channel-open*` flag that keeps track of whether or not the current polling channel is still open. Sometimes the channel can close on you, e.g. if the context is shut down while you're still polling, resulting in an unhandled ClosedChannelException. Hopefully this will be fixed upstream in JeroMQ, but as a work-around, ezzmq will now catch situations where you can't poll and set `*channel-open*` to false.

  * Added a `while-polling` macro that executes its body as long as the current thread is uninterrupted and `*channel-open*` remains true.

  * Adjusted `poll` and `polling` so that `*channel-open*` will be set to false if any of the following happen:
    * The context is shut down.
    * A ClosedChannelException is thrown (this error is caught internally; if you use `with-polling`, the only behavior you should notice is that the poller stops polling if the channel closes).
    * A call to `ZMQ.poll()` returns `-1`.

## 0.4.1 (2016-11-24)

* The `poll` function now returns a set of indexes representing sockets on which messages were received. See the Polling section of the README for an example.

## 0.4.0 (2016-11-07)

* When you want to create a SUB socket and you want to receive _all_ messages (not just particular topic(s)), you can now leave out the `:subscribe` option and ezzmq will use a default topic of `""` (all messages).

* Added a `polling` convenience macro and a `poll` function, to be used together. See the README for more info about polling with ezzmq.

## 0.3.0 (2016-10-28)

* Breaking change: the `:zmq-context` (`ZMQ.Context`) context type has been renamed to `:zmq.context`. This more clearly conveys that it uses a `ZMQ.Context`, not just that it is a ZMQ context in general.

## 0.2.0 (2016-10-23)

* Ensures that `setLinger(0)` is called when a ZMQ.Context is terminated.

* `worker-thread` macro abstracts away the boilerplate of catching `ETERM` and exiting cleanly in a worker thread. See README for more info.

* Ensures that any "active" contexts are terminated if the process is interrupted.

* `before-shutdown` and `after-shutdown` functions for adding custom before/after shutdown hooks for the current context. See README for more info.

## 0.1.2 (2016-10-16)

* `ezzmq.core/socket` will now accept a collection of strings instead of a single string as the value of the `:bind` and `:connect` options. This can be used to bind/connect a socket to multiple addresses.

## 0.1.1 (2016-10-13)

* Fixed use of `:zmq-context` (`ZMQ.Context`) context type so that it closes sockets for you before terminating.

  (`ZContext`, the default context type, already does this automatically.)

## 0.1.0 (2016-10-13)

* Initial release.

[lbbroker]: https://github.com/daveyarwood/ezzmq/blob/master/examples/zguide/lbbroker.boot

