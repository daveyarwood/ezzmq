# CHANGELOG

## 0.1.2 (10/16/16)

* `ezzmq.core/socket` will now accept a collection of strings instead of a single string as the value of the `:bind` and `:connect` options. This can be used to bind/connect a socket to multiple addresses.

## 0.1.1 (10/13/16)

* Fixed use of `:zmq-context` (`ZMQ.Context`) context type so that it closes sockets for you before terminating.

  (`ZContext`, the default context type, already does this automatically.)

## 0.1.0 (10/13/16)

* Initial release.
