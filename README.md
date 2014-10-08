# Blazar - Fiber-blocking HTTP / WebSocket Client and Server APIs

Straightforward, lightning-fast **Client/Server fiber-blocking HTTP and WebSocket APIs** for Clojure, plus a **fiber-blocking ring adapter**.

**Blazar** uses:

- The [Timbre](https://github.com/ptaoussanis/timbre) Clojure logging framework
- [http-kit](http://http-kit.org/)'s asynchronous HTTP/WebSocket Server and HTTP Client APIs
- [Gniazdo](https://github.com/stylefruits/gniazdo)'s asynchronous WebSocket Client API
- The excellent [Pulsar](https://github.com/puniverse/pulsar) library based on Parallel Universe's [Comsat](https://github.com/puniverse/comsat) stack to provide straightforward, lightning-fast fiber-blocking HTTP/WebSocket Client and Server APIs, and a fiber-blocking ring adapter, for Clojure on top of the above

Remember to plug in Parallel Univers's [Quasar](https://github.com/puniverse/quasar)'s JVM classes instrumentation.

## Requirements

Clojure 1.6 is required to run Blazar.

## Status

Basic tests run ok, but expect some bumpiness as the project is still **very** young.

## Getting started

With leiningen use:

```clojure
[com.dreamtimecircles/blazar "0.1.1-SNAPSHOT"]
```

## Usage

The [blazar.examples](../master/src/blazar/examples) package contains complete working examples, a short introduction based on them follows.

### Ring Adapter

```clojure
(ns blazar.examples.ring
  (:require
    [co.paralleluniverse.pulsar.core :as pc]
    [blazar.http.server :as bs]))

(pc/join (pc/spawn-fiber #(bs/start-fiber-ring-adapter "localhost" 8080 (fn [req] "Hello world!"))))
```

### HTTP client

The API arguments are fully compatible with [http-kit's client API](http://http-kit.org/client.html)

```clojure
(use '(blazar.http client))
(use '[co.paralleluniverse.pulsar.core :only (spawn-fiber join)])

(join (spawn-fiber
  ; Fiber-blocking call
  #(http-get "http://localhost:8080")))
```

### WebSocket client

```clojure
(use '(blazar.http client))
(use '[co.paralleluniverse.pulsar.core :only (spawn-fiber join)])

(join (spawn-fiber #(let [
  ; This opens a new WebSocket handle, fiber-blocking call
  handle (ws-open "ws://localhost:8080")]

  ; This will send WebSocket data, fiber-blocking call
  (ws-snd handle "Hello world!")     ; Optional ws-rcv parameters: :close? flag, :timeout and :timeout-unit

  ; This will send WebSocket data, fiber-blocking call
  (println (:value (ws-rcv handle))) ; :close and :error are also possible keys in result
                                     ; Optional ws-rcv parameters: :close? flag, :timeout and :timeout-unit

  ; This will close the handle
  (ws-close handle))))
```

### HTTP/websocket server

```clojure
(use '(blazar.http server))
(use '[co.paralleluniverse.pulsar.core :only (spawn-fiber join)])

(join (spawn-fiber #(let [
  ; This will spawn a new server listening on interface localhost, port 8080, and return a server handle
  srv (bind "localhost" 8080)

  ; This will start listening, fiber-blocking call
  conn (listen srv)
  proto (first conn) ; Either :http or :ws

  ; This will receive incoming data, fiber-blocking call
  data (rcv conn)]

  (cond
    (= proto :ws)                    ; Websocket full-duplex channel is now open until either
                                     ; server or client close it
      (cond
        (:ws-text data)              ; Websocket data
          (if (= data "Close")
            (close handle)           ; Client requested to close, so doing it
            (snd handle "OK"))       ; Echoing (fiber-blocking call). Optional :close?, :timeout and
                                     ; :timeout-unit parameters
        (:ws-close data) nil)        ; Websocket termination, doing nothing
    (= proto :http)                  ; Plain HTTP connection
      (snd handle "Hello world!"))   ; Sends single HTTP response
                                     ; in http kit-compatible format,
                                     ; e.g. string or ring response map

  ; This closes the handle if not closed already
  (close handle)

  ; This unbinds the server
  (unbind server))))
```


## License

Blazar is free software published under the following license:

```
Copyright © 2014 Fabio Tudone

This program and the accompanying materials are dual-licensed under
either the terms of the Eclipse Public License v1.0 as published by
the Eclipse Foundation

  or (per the licensee's choosing)

under the terms of the GNU Lesser General Public License version 3.0
as published by the Free Software Foundation.
```

[![githalytics.com alpha](https://cruel-carlota.gopagoda.com/6f172ebdf11f5b084127c9470cc7c887 "githalytics.com")](http://githalytics.com/dreamtimecircles/blazar)