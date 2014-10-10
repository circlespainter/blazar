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

(ns ^{ :author "circlespainter" } blazar.examples.httpclt
  "Blazar HTTP Client example"
  (:use
    [co.paralleluniverse.pulsar.core
      :only [spawn-fiber join]]
    blazar.http.client
    clojure.stacktrace)
  (:require
    [taoensso.timbre :as timbre]))

(timbre/set-config! [:timestamp-pattern] "yyyy-MMM-dd HH:mm:ss.SSS ZZ")
(timbre/set-level! :debug)

(defn -main []
  ; You can use all of http-kit's supported HTTP methods
  ; You can pass in http-kit options through an additional map
  ; Return values are http-kit's client one
  (join (spawn-fiber #(try
    (println (http-get "http://localhost:8080"))
    (catch Throwable t (print-cause-trace t))))))