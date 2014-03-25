(ns blazar.utils
  (:import (co.paralleluniverse.strands Strand))
  (:use
    [co.paralleluniverse.pulsar.core :as pc]
    [taoensso.timbre :as timbre :only (trace debug info warn error fatal spy)]
    blazar.utils-profile))

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

(defn record-fiber [type id fiber]
  (swap! fibers #(merge % {[type id] fiber}))
	(debug "[DUMP]" (keys @fibers)))

(defn unrecord-fiber [type id]
  (swap! fibers #(dissoc % [type id]))
	(debug "[DUMP]" (keys @fibers)))

