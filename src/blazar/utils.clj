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

(ns ^{ :author "circlespainter" :internal true } blazar.utils
  "Various internal Blazar utilities"
  (:import (co.paralleluniverse.strands Strand))
  (:use
    [co.paralleluniverse.pulsar.core :as pc]
    [taoensso.timbre :as timbre :only (trace debug info warn error fatal spy)]
    blazar.utils-profile))

; Auto-reload support, profile-overridden
(defn- check-namespace-changes [track]
  (do
    (try
      (doseq [ns-sym (track)]
        (debug "Reloading namespace: " ns-sym)
        (require ns-sym :reload))
      (catch Throwable e (.printStackTrace e)))
    (Thread/sleep 500)))

(def ^:private thread (ref nil))

(defn start-nstracker! []
  (start-nstracker-profile! thread check-namespace-changes))

(def ^:private fibers (atom {}))

(defn ^:internal record-fiber [type id fiber]
  (swap! fibers #(merge % {[type id] fiber}))
	(debug "[DUMP]" (keys @fibers)))

(defn ^:internal unrecord-fiber [type id]
  (swap! fibers #(dissoc % [type id]))
	(debug "[DUMP]" (keys @fibers)))