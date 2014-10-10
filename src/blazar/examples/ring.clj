; Blazar: straightforward, lightning-fast Client/Server fiber-blocking
; HTTP and WebSocket APIs for Clojure, plus a fiber-blocking ring adapter.
;
; Copyright (C) 2014 Fabio Tudone. All rights reserved.
;
; This program and the accompanying materials are dual-licensed under
; either the terms of the Eclipse Public License v1.0 as published by
; the Eclipse Foundation
;
;   or (per the licensee's choosing)
;
; under the terms of the GNU Lesser General Public License version 3.0
; as published by the Free Software Foundation.

; TODO Review for compliance with https://github.com/bbatsov/clojure-style-guide

(ns ^{ :author "circlespainter" } blazar.examples.ring
  "Blazar HTTP Client example"
  (:require
    [co.paralleluniverse.pulsar.core :as pc]
    [blazar.http.server :as bs]))

(pc/join (pc/spawn-fiber #(bs/start-fiber-ring-adapter "localhost" 8080 (fn [req] "Hello world!"))))