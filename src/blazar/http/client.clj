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

(ns ^{ :author "circlespainter" } blazar.http.client
  "Blazar Client APIs"
  (:import (java.util.concurrent TimeUnit))
  (:refer-clojure :exclude [promise await])
  (:require
    [co.paralleluniverse.pulsar.core :as pc]
    [org.httpkit.client :as hc]
    [gniazdo.core :as ws]
    [taoensso.timbre :as timbre
      :refer (
        log trace  debug  info  warn  error  fatal  report
        logf tracef debugf infof warnf errorf fatalf reportf
        spy logged-future with-log-level)]))

(set! *warn-on-reflection* true)

(defmacro ^:private deffiberreq [n method]
  "Defines an 'http-' prefixed Client API function named after the HTTP method"
  `(pc/defsfn
     ~n
      ([^String ~'url]
        (pc/await ~(symbol "hc" (name method)) ~'url {}))
      ([^String ~'url ~'opts]
        (pc/await ~(symbol "hc" (name method)) ~'url ~'opts))))

; API functions
(deffiberreq http-get :get)
(deffiberreq http-delete :delete)
(deffiberreq http-head :head)
(deffiberreq http-post :post)
(deffiberreq http-put :put)
(deffiberreq http-options :options)
(deffiberreq http-patch :patch)

(declare ws-close)

(pc/defsfn ws-open [url]
  "Opens a WebSocket handle (fiber-blocking)"
  ; TODO De-uglify and cleanup, empty bindings for side-effects are smelly
  (let [ch-session (pc/channel 1)
        ch-recv (pc/channel 1)
        err (atom nil)
        closed (atom nil)
        socket
          (ws/connect
            url
            :on-connect
              #(do
                (debug "[Blazar WS client API: on-connect async] Connected to" url "through" %)
                (pc/snd ch-session %))
            :on-receive
              #(do
                (debug "[Blazar WS client API: on-receive async] Received" % ", pushing to receive channel" ch-recv)
                (pc/snd ch-recv %))
            :on-error
              #(do
                (debug "[Blazar WS client API: on-error async] Received error" % ", setting to atom")
                (swap! err (fn [x] %)))
            :on-close
              #(do
                (debug "[Blazar WS client API: on-close async] Received closing" %1 %2 ", setting to atom")
                (swap! closed (fn [x] {:code %1 :reason %2}))
                (pc/snd ch-recv {:closed @closed})))
          _ (debug "[Blazar WS client API: ws-open] waiting for websocket session")
          session (pc/rcv ch-session)
          _ (debug "[Blazar WS client API: ws-open] Got websocket session " session)
          ret {:socket socket :session session :ch-recv ch-recv :err err :closed closed}]
    (debug "[Blazar WS client API: ws-open] Connected to" url ", returning handle" ret)
    ret))

(pc/defsfn ws-rcv [{socket :socket ch-recv :ch-recv err :err closed :closed}
                   & {:keys [close? timeout timeout-unit]
                      :or {close? false timeout -1 timeout-unit (. TimeUnit SECONDS)}}]
  "Receives a message from a WebSocket handle (fiber-flocking)"
  ; TODO De-uglify and cleanup, empty bindings for side-effects are smelly
  (cond
    @closed (do (debug "[Blazar WS client API: ws-rcv] Closed handle" @closed) {:closed @closed})
    @err (do (debug "[Blazar WS client API: ws-rcv] Error in handle" @err) (throw @err))
    :else
      (let [_ (debug "[Blazar WS client API: ws-rcv] Receiving from receive channel" ch-recv)
            msg (cond (< timeout 0) (pc/rcv ch-recv) (= timeout 0) (pc/try-rcv ch-recv) (> timeout 0) (pc/rcv ch-recv timeout timeout-unit))
            ret (if (and (map? msg) (:closed msg)) msg {:value msg})]
        (debug "[Blazar WS client API: ws-rcv] Received and returning" ret "from" ch-recv)
        (if close? (do (debug "[Blazar WS client API: ws-rcv] Closing after receive, as requested") (ws-close socket)))
        ret)))

(defn ws-snd [{socket :socket err :err closed :closed} msg & {:keys [close?] :or {close? false}}]
  "Sends a message through a WebSocket handle"
  (cond
    @closed (do (debug "[Blazar WS client API: ws-snd] Closed handle" @closed) {:closed @closed})
    @err (do (debug "[Blazar WS client API: ws-snd] Error in handle" @err) (throw @err))
    :else
      (do
        (debug "[Blazar WS client API: ws-snd] Sending" msg "to" socket)
        (ws/send-msg socket msg)
        (debug "[Blazar WS client API: ws-snd] Sent" msg "to" socket)
        (if close? (do (debug "[Blazar WS client API: ws-snd] Closing after send, as requested") (ws-close socket))))))

(defn ws-close [{socket :socket err :err closed :closed}]
  "Closes a WebSocket handle"
  (cond
    @closed (do (debug "[Blazar WS client API: ws-close] Closed handle" @closed) {:closed @closed})
    @err (do (debug "[Blazar WS client API: ws-close] Error in handle" @err) (throw @err))
    :else
      (do
        (debug "[Blazar WS client API: ws-close] Closing socket" socket)
        (ws/close socket)
        (debug "[Blazar WS client API: ws-close] Closed socket" socket))))

(defn ws-closed? [{closed :closed}]
  "Tells if a WebSocket handle is closed"
  (debug "[Blazar WS client API: ws-closed?] Returning" @closed) @closed)

(defn ws-error? [{err :err}]
  "Tells if a WebSocket handle is in error"
  (debug "[Blazar WS client API: ws-error?] Returning" @err) @err)