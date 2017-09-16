# CHANGELOG

## Unreleased

* Added a `:timeout` keyword option to both `send-msg` and `receive-msg`. When
  used, the socket temporarily gets its send/receive timeout set to the provided
  number of milliseconds. After the send/receive completes or times out, the
  original timeout value of the socket is restored.

* `with-send-timeout` and `with-receive-timeout` macros provide a scope where
  the send/receive timeout has a particular value. (These macros are used under
  the hood in `send-msg` and `receive-msg` to implement the `:timeout` option.)

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
