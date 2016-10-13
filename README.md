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

## Contributing

If you like the direction I'm going with this library and you have things you'd like to do with it that it currently can't do, please [file an issue](https://github.com/daveyarwood/ezzmq/issues) and we'll figure it out together. I want ezzmq to be an awesome and sensible way to build ZeroMQ apps in Clojure.

## License

Copyright © 2016 Dave Yarwood

Distributed under the Eclipse Public License version 1.0.
