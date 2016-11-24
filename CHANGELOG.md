# CHANGELOG

## 0.4.1 (11/24/16)

* The `poll` function now returns a set of indexes representing sockets on which messages were received. See the Polling section of the README for an example.

## 0.4.0 (11/7/16)

* When you want to create a SUB socket and you want to receive _all_ messages (not just particular topic(s)), you can now leave out the `:subscribe` option and ezzmq will use a default topic of `""` (all messages).

* Added a `polling` convenience macro and a `poll` function, to be used together. See the README for more info about polling with ezzmq.

## 0.3.0 (10/28/16)

* Breaking change: the `:zmq-context` (`ZMQ.Context`) context type has been renamed to `:zmq.context`. This more clearly conveys that it uses a `ZMQ.Context`, not just that it is a ZMQ context in general.

## 0.2.0 (10/23/16)

* Ensures that `setLinger(0)` is called when a ZMQ.Context is terminated.

* `worker-thread` macro abstracts away the boilerplate of catching `ETERM` and exiting cleanly in a worker thread. See README for more info.

* Ensures that any "active" contexts are terminated if the process is interrupted.

* `before-shutdown` and `after-shutdown` functions for adding custom before/after shutdown hooks for the current context. See README for more info.

## 0.1.2 (10/16/16)

* `ezzmq.core/socket` will now accept a collection of strings instead of a single string as the value of the `:bind` and `:connect` options. This can be used to bind/connect a socket to multiple addresses.

## 0.1.1 (10/13/16)

* Fixed use of `:zmq-context` (`ZMQ.Context`) context type so that it closes sockets for you before terminating.

  (`ZContext`, the default context type, already does this automatically.)

## 0.1.0 (10/13/16)

* Initial release.
