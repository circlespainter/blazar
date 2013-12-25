(ns main-actors
  (:refer-clojure :exclude [promise await])
  (:require
    [co.paralleluniverse.pulsar.core :as pc]
    [blazar.http.server :as bs]
    [blazar.http.client :as bc]
    [clojure.core.match :as m]))

(defn -main []
  (let
      [
        client-boot-channel (pc/channel)
        _ (println "Spawning server fiber")
        server-fiber
        (pc/spawn-fiber ; Server
          #(try
            (let
                [_ (println "High-performance fiber/actor-based Server: creating server istance")
                 srv (bs/create-server)]
              (println "High-performance fiber/actor-based Server: activating @ [http|ws]://localhost:8080")
              (bs/bind srv "localhost" 8080)
              (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080 active, activating client by sending 'GO' over client-sync channel")
              (pc/snd client-boot-channel "GO")
              (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080 active, initiating serving loop")
              (loop []
                (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: getting incoming data")
                (let [d (bs/get-data srv)]
                  (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: got data '" d "'")
                  (m/match
                    [d]
                    [{:closed _}]
                    (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: a connection has been closed")
                    [{:websocket? ws :data data :reply-to to}]
                    (do
                      (println (str "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: data '" data " received, websocket = " ws))
                      (m/match
                        [data]
                        ["!!!CLOSE!!!"]
                        (do
                          (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: received '!!!CLOSE!!!' message, sending closing message")
                          (bs/send-data srv to "!!!CLOSING!!!" true)
                          (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: received '!!!CLOSE!!!' message, sent closing message"))
                        [x]
                        (do
                          (println (str "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: received normal message '" x "', replying 'Hello world!'"))
                          (bs/send-data srv to "Hello world!" (not ws))
                          (println (str "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: received normal message '" x "', sent 'Hello world!'")))))
                    :else (println "High-performance fiber/actor-based Server @ [http|ws]://localhost:8080: got unknown data '" d "'")))
                (recur)))
            (catch Throwable t (do (.printStackTrace t) (println "High-performance fiber/actor-based Server got exception '" t "', dying!") (throw t)))))
        _ (println "Spawned server fiber")
        client-fiber
        (pc/spawn-fiber ; Client
          (fn []
            (try
              (let [wait-chan (pc/channel)]
                (println "High-performance fiber/actor-based Client: waiting for 'GO' (server ready) on server-sync channel")
                (println "High-performance fiber/actor-based Client: received '" (pc/rcv client-boot-channel) "'")
                (pc/rcv wait-chan 5 :sec)
                (println "High-performance fiber/actor-based Client:, opening 'ws://localhost:8080'")
                (let [ws (bc/ws-open "ws://localhost:8080")]
                  (println "High-performance fiber/actor-based Client @ ws://localhost:8080: websocket opened successfully")
                  (pc/rcv wait-chan 5 :sec)
                  (println "High-performance fiber/actor-based Client @ ws://localhost:8080: sending 'Yo!'")
                  (bc/ws-send ws "Yo!")
                  (println "High-performance fiber/actor-based Client @ ws://localhost:8080: 'Yo!' message sent, getting reply")
                  (println "High-performance fiber/actor-based Client @ ws://localhost:8080: '" (bc/ws-get ws) "' message received")
                  (println "High-performance fiber/actor-based Client @ ws://localhost:8080: sending '!!!CLOSE!!!' message")
                  (bc/ws-send ws "!!!CLOSE!!!")
                  (println "High-performance fiber/actor-based Client @ ws://localhost:8080: '!!!CLOSE!!!' message sent, getting reply")
                  (println "High-performance fiber/actor-based Client @ ws://localhost:8080: '" (bc/ws-get ws) "' message received")
                  (println "High-performance fiber/actor-based Client: GETting 'http://localhost:8080'")
                  (println "High-performance fiber/actor-based Client @ http://localhost:8080: '" (bc/get "http://localhost:8080" {}) "' response received")))
              (catch Throwable t (do (.printStackTrace t) (println "High-performance fiber/actor-based Client got exception '" t "', dying!") (throw t))))))]
    (pc/join client-fiber)
    (pc/join server-fiber)))