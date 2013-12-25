(ns blazar.utils-profile
  (:use ns-tracker.core))

(defn start-nstracker-profile! [thread check-namespace-changes]
  (if (not @thread)
    (let
        [track (ns-tracker ["src" "checkouts"])
         t (Thread. #(while true (check-namespace-changes track)))]
      (dosync
        (doto t (.setDaemon true) (.start))
        (alter thread (fn [ov] t))))))