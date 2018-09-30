# ezzmq

[![Clojars Project](https://img.shields.io/clojars/v/io.djy/ezzmq.svg)](https://clojars.org/io.djy/ezzmq)

A small library of opinionated [ZeroMQ](http://zeromq.org) boilerplate for Clojure.

## Background

ZeroMQ is great, but its API is a little too low-level to be convenient for rapid development. A lot of the time, you just want to bind/connect a particular type of socket and send and receive messages on it. Sometimes the amount of boilerplate code you need to write to accomplish this can feel tedious. That's why this library exists.

### Similar Projects

#### Official ZeroMQ libraries

* [jzmq](https://github.com/zeromq/jzmq) is the Java language binding for ZeroMQ. It uses the C library, libzmq, via JNI. This is probably the ideal way to use ZeroMQ in a Java or Clojure project if you are OK with native dependencies.

* [JeroMQ](https://github.com/zeromq/jeromq) is a pure Java implementation of libzmq. It has decent performance compared to libzmq and uses the same API as jzmq. Only ZMTP 2.0 / ZMQ 3.2.5 is supported, but I haven't found that to be a problem so far.

* [cljzmq](https://github.com/zeromq/cljzmq) provides Clojure bindings for ZeroMQ. Because jzmq and JeroMQ use the same API, cljzmq can be used for ZeroMQ both in pure Java form and via libzmq. To do it the JeroMQ (pure Java) way, you add `:exclusions [org.zeromq/jzmq]` to the cljzmq dependency vector in your build.boot or project.clj.

#### Other ZeroMQ libraries

* [jilch](https://github.com/mpenet/jilch) is a bare-bones Clojure wrapper around JeroMQ.

### Motivation

Why another ZeroMQ library? Well, in writing a handful of Clojure applications that use ZeroMQ, none of the above libraries came close enough to the kind of experience I wanted. Let me explain.

My first approach was to go high-level and use the existing Clojure libraries. I started with jilch because it was specifically a wrapper for JeroMQ, but I found it to be too minimal (barely better than using JeroMQ directly via Java inter-op), out of date (having been last updated 4 years ago), and generally lacking in ZeroMQ features that are available with JeroMQ.

Then I realized that cljzmq is compatible with JeroMQ if you exclude the jzmq dependency, so I tried that. It's pretty good, and it allows you to use some nice Clojure idioms like `with-open`. But for the most part, I found it also to be a thin wrapper around JeroMQ.

The more I learned about writing ZeroMQ programs and translated the Java examples in the (excellent) ZeroMQ guide, the more I found myself gravitating toward just importing JeroMQ and using it via Java inter-op. It's not a bad way to go.

But JeroMQ is an idiomatic Java library, and idiomatic Java code tends not to translate very well into idiomatic Clojure. I quickly grew tired of all the boilerplate I was writing. I started wanting to take all the patterns I was repeating and turn them into something reusable. So, here we are.

### This is an opinionated library

I'm not trying to make a "one size fits all" library. ezzmq exists to fill a niche created by the following opinions:

- **I don't care about using libzmq as a native dependency.** This library builds on top of JeroMQ, the pure Java implementation of libzmq. I've found that it works great, at least for my purposes. YMMV.

- **I don't want to work at a low level.** ZeroMQ is a low-level thing, and that's great. It needs to be low-level, and it's good that it's low-level because it makes it more flexible. But the trade-off is that you end up writing more boilerplate code. This library swings the other way; you're sacrificing a little flexibility so that you can write code that works, faster and easier, with less boilerplate. I'm going to try my best to add little nooks and crannies you can hook into to customize ezzmq to suit your own needs, but keep in mind that eliminating boilerplate is a higher priority.

- **I'm kinda making this up as I go.** The first step is to make it easy to copy the clean, simple examples in the ZeroMQ guide and make them work while writing a minimum of boilerplate code. As the examples get more complicated and more flexibility is needed, then ezzmq will get a little more flexible. But convenience is the primary goal here, not flexibility (see the last bullet point). So, I'm going to start simple and build this thing out over time in whatever ways make the most sense.

If you're on board with these opinions, then ezzmq will probably be up your alley!

## Usage

> To see some practical examples of how to use ezzmq, you may be interested in checking out [these implementations](https://github.com/daveyarwood/ezzmq/tree/master/examples/zguide) of the official ZeroMQ [ZGuide](http://zguide.zeromq.org/) examples.

### ZMQ Context

Any program you write that uses ZMQ will involve working with sockets. To create and manage sockets, you need a **context**.

In JeroMQ (the pure-Java ZMQ library on which ezzmq is based), there are at least two different types of contexts you can use. ZContext is the newest and recommended way to create a context, whereas ZMQ.Context is legacy and will probably be deprecated in the near future.

ezzmq uses ZContext by default, but you can bind `ezzmq.core/*context-type*` to `:zmq.context` to use a ZMQ.Context instead if you want.

The typical workflow of a single ZMQ thread or process is this:

- Create a context.
- Use the context to create one or more sockets.
- Bind/connect the sockets to ports.
- Send/receive messages to/from the socket in a loop until either some condition is met (e.g. receiving a "stop" message of some kind on a socket) or the thread/process is interrupted.
- Close all the sockets.
- Destroy the context.

ezzmq handles creating a context and eventually shutting down the context, including closing all of its sockets, for you so you don't have to worry about it. All you have to do is use the `ezzmq.core/with-new-context` macro and do all of your work inside of its scope:

```clojure
(require '[ezzmq.core :as zmq])

(zmq/with-new-context
  (comment "do work here"))
```

Once all the work is done, any sockets created will be closed and the context will be destroyed.

#### Before/after shutdown actions

ezzmq adds a shutdown hook to ensure that the context is shut down cleanly even if the process is interrupted, e.g. if you press Ctrl-C while it's still running. This means you don't have to worry about adding a shutdown hook yourself.

If there are special actions you would like to happen before or after the context is terminated, you can use `ezzmq.core/before-shutdown` and `ezzmq.core/after-shutdown`:

```clojure
(zmq/with-new-context
  (zmq/before-shutdown
    (println "Shutting down context..."))

  (zmq/after-shutdown
    (println "Done."))

  (comment "do work here"))
```

#### Worker threads

Shutting down a context with JeroMQ can be a little tricky when you are sharing a single process's context across multiple threads. This is a common pattern when you have a main thread and an asynchronous "worker thread" and they communicate with each other via an inproc socket.

In cases like this, when you terminate the context, the worker thread is interrupted; this is accomplished by throwing an `ETERM` ZMQException on the worker thread if the worker thread is doing some ZMQ-related blocking action, like waiting to receive a message.

The ["recommended" way of handling this](https://github.com/zeromq/jeromq/blob/master/src/test/java/guide/interrupt.java) is for the worker thread to wrap any ZMQ-related blocking actions in a try/catch and handle ZMQExceptions when the error code is `ETERM`.

ezzmq abstracts away this boilerplate code for you. To take advantage of this abstraction, use the `ezzmq.core/worker-thread` macro instead of a `future`. (Under the hood, `worker-thread` creates a future and wraps its execution in a try/catch that handles the `ETERM` exception.)

For an example usage of the `worker-thread` macro, see [the ezzmq translation of the zguide interrupt example](https://github.com/daveyarwood/ezzmq/blob/master/examples/zguide/interrupt.boot).

### Sockets

Use the `ezzmq.core/socket` function to create and bind/connect sockets in a single function call. You should use a `let` binding so you have a reference to the sockets and can interact with them later:

```clojure
(zmq/with-new-context
  (let [socket (zmq/socket :rep {:bind "tcp://*:12345"})]
    (comment "do stuff with socket here")))
```

The values for `:bind` and `:connect` can be either strings or collections of strings, each representing an address to bind/connect with that socket.

In the case of SUB sockets, you can also set the subscribed topic(s) in this step via the `:subscribe` key:

```clojure
(zmq/with-new-context
  (let [socket (zmq/socket :sub {:connect   "tcp://*:12345"
                                 :subscribe "foo"})]
    (comment "do stuff with socket here")))
```

The value for `:subscribe` can be either a string, a byte array, or a collection consisting of any combination of strings and byte arrays, each representing one subscription:

```clojure
(zmq/with-new-context
  (let [socket (zmq/socket :sub {:connect   "tcp://*:12345"
                                 :subscribe ["foo" "bar" "baz"]})]
    (comment "do stuff with socket here")))
```

If you leave out the `:subscribe` key, ezzmq will set a default topic of `""` for you and you will receive all published messages.

### Messages

ZeroMQ messages can be single- or multipart. In low-level usage of ZeroMQ, if a message is multipart you have to keep receiving message frames manually until there are no more parts to receive.

ezzmq simplifies this by representing a message as a vector of frames. When you receive a message and the message contains multiple parts, you don't have to call `receive-msg` again, you just get the whole thing.

Each frame is a byte array by default.

To receive a message, use `ezzmq.core/receive-msg`:

```clojure
(zmq/with-new-context
  (let [socket (zmq/socket :rep {:bind "tcp://*:12345"})]
    (while true
      (let [msg (zmq/receive-msg socket)]
        (comment "do something with msg")))))
```

`(zmq/receive-msg socket)` will return a vector of byte arrays, where each byte array is one frame of the message.

`(zmq/receive-msg socket {:stringify true})` will return a vector of strings. This is convenient when you know the message frames are supposed to be strings.

To send a message, use `ezzmq.core/send-msg`:

```clojure
(zmq/with-new-context
  (let [socket (zmq/socket :rep {:bind "tcp://*:12345"})]
    (while true
      (let [msg (zmq/receive-msg socket)]
        (zmq/send-msg socket "This is my response to your message.")))))
```

The message you send can either be a single string, a single byte array, or a sequence containing any combination of strings and byte arrays:

```clojure
(zmq/send-msg socket ["this" "is" "a" "multipart" (.getBytes "message")])
```

### Polling

Sometimes you want to handle messages coming from multiple sockets at once. The ZeroMQ way to do this is by **polling**. The typical boilerplate code you would write for this would do the following:

- Create a Poller instance. When you do this, you have to specify how many sockets you'll be polling for some stupid reason. (Can't it just keep count as you register them?)
- Register the sockets you want to poll with the poller. This includes specifying which types of events you want to poll for: `POLLIN` (is there a message to receive?), `POLLOUT` (can I send a message?) and/or `POLLERR` (is the socket in an error state?).
- Poll! (This updates the state of the poller.)
- Iterate through each socket you want to check. For each socket:
  - Check for `POLLIN`, `POLLOUT` and/or `POLLERR`, depending on what you want to do. You have to supply the index of the socket you want to check when you do this, because that's _totally_ something we want to worry about keeping track of.
  - If the socket is in that state, then do some handling action. For example, if you're checking `POLLIN` and there is in fact a message to receive, then the handling action would be receiving a message from the socket and doing something with it.

This is pretty ugly, but nothing we can't abstract away. In ezzmq it works like this:

```clojure
(zmq/polling {:receive-opts {:stringify true}}
  [socket-a :pollin [msg]
   (println "Received msg:" msg)

   socket-b :pollin [msg]
   (println "Received msg:" msg)

   socket-c :pollout []
   (zmq/send-msg socket-c "hey here is a msg")

   socket-d :pollerr []
   (throw (Exception. "SOCKET D HAS GONE ROGUE"))]

  (zmq/while-polling
    (zmq/poll {:timeout 1000})))
```

#### `polling`

In the code above, the `polling` macro sets up a poller for you to check 4 previously defined sockets, in the order listed.

Whenever `socket-a` or `socket-b` have messages to receive, we go ahead and receive them and execute some handling code, which in the above example is just printing the message we received.

Whenever `socket-c` is in a state where we can send a message, we send a message.

Whenever `socket-d` is in an error state, we flip out and throw an exception. (Don't do this in practice.)

See [mspoller.boot](https://github.com/daveyarwood/ezzmq/blob/master/examples/zguide/mspoller.boot) for a working example of a situation where we poll two sockets (a SUB socket and a PULL socket) and print all the messages we receive from either socket.

#### `while-polling`

The body of the `while-polling` macro is executed in a loop until it is no longer possible to poll. This can happen due to a handful of problems that can crop up, all of which `while-polling` handles for you behind the scenes, e.g. the context being shut down or the process being interrupted by a Ctrl-C.

#### `polling?`

There will often be situations where you don't just want to poll forever until the process is interrupted. For example, you may want to define a `running?` atom in your program and set its value to `false` when you receive a specific "shutdown" message on a socket.

You can use the `polling?` function in cases like this to give you more control over the conditions that determine whether or not to keep polling.

`polling?` returns true as long as the current thread is not interrupted and the poller you are using can still be polled.

Under the hood, `while-polling` is implemented as:

```clojure
(while (polling?)
  ...)
```

Here is an example of how to use `polling?` along with additional conditions to determine when to stop polling:

```clojure
(let [running? (atom true)]
  (zmq/polling {:receive-opts {:stringify true}}
    [socket :pollin [msg]
     (if (= ["STOP"] msg)
       (reset! running? false))]

    (while (and (zmq/polling?) @running?)
      (zmq/poll {:timeout 1000}))))
```

#### `poll`

`poll` triggers a chain of events where we go through the sockets in order,
check to see if certain conditions are met, and act accordingly. For example,
for `:pollin` items, if there is a message to receive, then we receive a message
and call the provided handling code on the message.

For cases where you need to take certain actions based on which sockets had messages each time you poll, you can use the return value of `ezzmq.core/poll`, which is the set of sockets that had messages.

For example:

```clojure
(zmq/polling {:receive-opts {:stringify true}}
  [socket-a :pollin [msg]
   (println "Received msg:" msg)

   socket-b :pollin [msg]
   (println "Received msg:" msg)

   socket-c :pollout []
   (zmq/send-msg socket-c "hey here is a msg")

   socket-d :pollerr []
   (throw (Exception. "SOCKET D HAS GONE ROGUE"))]

  (zmq/while-polling
    (let [got-msgs (zmq/poll {:timeout 1000})]
      (when-not (contains? got-msgs socket-a)
        (println "no msg received on socket-a")))))
```

#### Low-level polling API

In more complex use cases, it sometimes makes sense to set up multiple pollers
and poll them conditionally. ezzmq has a lower-level API for such use cases.

For details, see the [`lbbroker`
example](https://github.com/daveyarwood/ezzmq/blob/master/examples/zguide/lbbroker.boot)
and the docstrings for `poller`, `polling?` and `poll` in the `ezzmq.poll`
namespace.

## Contributing

If you like the direction I'm going in with this library and you have things
you'd like to do with it that it currently can't do, please [file an
issue](https://github.com/daveyarwood/ezzmq/issues) and we'll figure it out
together. I want ezzmq to be an awesome and sensible way to build ZeroMQ apps in
Clojure.

## License

Copyright © 2016-2017 Dave Yarwood

Distributed under the Eclipse Public License version 1.0.
