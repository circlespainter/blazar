(ns wsclt
  (:use
    [co.paralleluniverse.pulsar.core
      :only [spawn-fiber join]]
    blazar.http.client
    clojure.stacktrace))

(defn -main []
  (join (spawn-fiber #(try
    (let [handle (ws-open "ws://localhost:8080")]
      (ws-snd handle "Hello world!")     ; Optional ws-rcv parameters: :close? flag, :timeout and :timeout-unit
      (println (:value (ws-rcv handle))) ; :close and :error are also possible keys in result
                                         ; Optional ws-rcv parameters: :close? flag, :timeout and :timeout-unit
      (ws-close handle))
    (catch Throwable t (print-cause-trace t))))))