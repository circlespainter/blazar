(ns blazar.examples.httpclt
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