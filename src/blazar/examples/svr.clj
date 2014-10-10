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

(ns ^{ :author "circlespainter" } blazar.examples.svr
  "Blazar HTTP+WebSocket server example"
  (:use
    [co.paralleluniverse.pulsar.core
      :only [spawn-fiber]]
    blazar.http.server
    clojure.stacktrace)
  (:require
    [taoensso.timbre :as timbre]))

(timbre/set-config! [:timestamp-pattern] "yyyy-MMM-dd HH:mm:ss.SSS ZZ")
(timbre/set-level! :debug)

(defn -main []
  (let [server (bind "localhost" 8080)]      ; Bind to localhost's IP and 8080 port
    (loop
      [
        [proto _ :as handle] (listen server) ; Optional :timeout and :timeout-unit parameters
        n 3                                  ; Accepts and processes 3 connections
      ]
      (spawn-fiber (fn [] (try
        (let [data (rcv handle)]             ; Receiving. Optional :close?, :timeout and :timeout-unit parameters
          (cond
            (= proto :ws)                    ; Websocket full-duplex channel is now open until either
                                             ; server or client close it
              (cond
                (:ws-text data)              ; Websocket data
                  (if (= data "Close")
                    (close handle)           ; Client requested to close, so doing it
                    (snd handle "OK"))       ; Echoing. Optional :close?, :timeout and :timeout-unit parameters
                (:ws-close data) nil)        ; Websocket termination, doing nothing

            (= proto :http)                  ; Plain HTTP connection

              (snd handle "Hello world!"))   ; Sends single HTTP response
                                             ; in http kit-compatible format,
                                             ; e.g. string or ring response map

          (close handle))                    ; Closes the handle if not closed already

        (catch Throwable t
          (print-cause-trace t)))))

      (if (> n 1)                            ; Accepts and processes 3 connections
        (recur (listen server) (- n 1))))

    (unbind server)))                        ; Unbinds server