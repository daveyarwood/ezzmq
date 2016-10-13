# CHANGELOG

## 0.1.1 (10/13/16)

* Fixed use of `:zmq-context` (`ZMQ.Context`) context type so that it closes sockets for you before terminating.

  (`ZContext`, the default context type, already does this automatically.)

## 0.1.0 (10/13/16)

* Initial release.
