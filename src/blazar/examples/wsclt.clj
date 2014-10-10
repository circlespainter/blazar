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

(ns ^{ :author "circlespainter" } blazar.examples.wsclt
  "Blazar WebSocket Client example"
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
  (join (spawn-fiber #(try
    (let [handle (ws-open "ws://localhost:8080")]
      (ws-snd handle "Hello world!")     ; Optional ws-rcv parameters: :close? flag, :timeout and :timeout-unit
      (println (:value (ws-rcv handle))) ; :close and :error are also possible keys in result
                                         ; Optional ws-rcv parameters: :close? flag, :timeout and :timeout-unit
      (ws-close handle))
    (catch Throwable t (print-cause-trace t))))))