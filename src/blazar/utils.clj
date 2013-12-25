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

(defn start-new-runtime-dumping-fiber! [millis & args]
  (pc/spawn-fiber
    #(loop [count (if-let [[n] args] n -1)]
      (Strand/sleep millis)
      (debug "[DUMP]" (keys @fibers))
      (if (> count 0) (recur (- count 1))))))

(defn record-fiber [type id fiber]
  (swap! fibers #(merge % {[type id] fiber})))

(defn unrecord-fiber [type id]
  (swap! fibers #(dissoc % [type id])))

