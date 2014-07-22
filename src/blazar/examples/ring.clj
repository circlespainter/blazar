(ns blazar.examples.ring
  (:require
    [co.paralleluniverse.pulsar.core :as pc]
    [blazar.http.server :as bs]))

(pc/join (pc/spawn-fiber #(bs/start-fiber-ring-adapter "localhost" 8080 (fn [req] "Hello world!"))))