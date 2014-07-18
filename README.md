# Blazar - Fiber-blocking HTTP / WebSocket Client and Server APIs

Straightforward, lightning-fast fiber-blocking HTTP / WebSocket Client and Server APIs for Clojure.

*Blazar* uses:

- The [Timbre](https://github.com/ptaoussanis/timbre) Clojure logging framework
- [http-kit](http://http-kit.org/)'s asynchronous HTTP/WebSocket Server and HTTP Client APIs
- [Gniazdo](https://github.com/stylefruits/gniazdo)'s asynchronous WebSocket Client API
- The excellent [Pulsar](https://github.com/puniverse/pulsar) library based on Parallel Universe's [Comsat](https://github.com/puniverse/comsat) stack to provide straightforward, lightning-fast fiber-blocking HTTP/WebSocket Client and Server APIs for Clojure on top of the above

Remember to plug Parallel Univers's [Quasar](https://github.com/puniverse/quasar)'s JVM classes instrumentation.

## Requirements

Clojure 1.6 is required to run Blazar.

## Usage

See the [blazar.examples](../master/src/blazar/examples) package

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