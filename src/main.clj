(ns main
  (:import (co.paralleluniverse.strands Strand))
  (:refer-clojure :exclude [promise await])
  (:require
    [clojure.string :as str]
    [clojure.core.match :as m]
    [taoensso.timbre :as timbre
     :refer (log  trace  debug  info  warn  error  fatal  report
                  logf tracef debugf infof warnf errorf fatalf reportf
                  spy logged-future with-log-level)]
    [co.paralleluniverse.pulsar.core :as pc]
    [blazar.http.server :as bs]
    [blazar.http.client :as bc]
    [blazar.utils :as utils]))

(pc/defsfn server-connection-handler [[proto handle :as full] srv]
  (try
    (let
        [
         _ (debug "High-performance fiber-blocking Server connection handle @ [http[s]|ws[s]]://localhost:8080: got connection '" full "', getting initial data")
         first-data (bs/rcv full)]
      (cond
        (= proto :ws)
        (do
          (debug "High-performance fiber-blocking Server connection handle @ ws[s]://localhost:8080 (loop): echoed, starting receive/echo loop")
          (loop [data first-data]
            (if (and data (not (= data {:ws-text "!!!CLOSE!!!"})) (not (:ws-close data)))
              (do
                (debug "High-performance fiber-blocking Server connection handle @ ws[s]://localhost:8080 (loop): got data '" data "', echoing")
                (bs/snd! full (:ws-text data))
                (debug "High-performance fiber-blocking Server connection handle @ ws[s]://localhost:8080 (loop): echoed, looping back to receive")
                (recur (bs/rcv full)))
              (do
                (debug "High-performance fiber-blocking Server connection handle @ ws[s]://localhost:8080 (loop): closing loop after receiving" data)
                (bs/close! full)
                (debug "High-performance fiber-blocking Server connection handle @ ws[s]://localhost:8080 (loop): closed, now unbinding")
                (bs/unbind! srv)
                (debug "High-performance fiber-blocking Server connection handle @ ws[s]://localhost:8080 (loop): unbound and exiting")
                (utils/unrecord-fiber :main-server-handle (bs/ch-id full))
                ; TODO check: there shouldn't be active/blocked fibers anymore, the program should exit
                ))))
        (= proto :http)
        (do
          (debug "High-performance fiber-blocking Server connection handle @ http[s]://localhost:8080: got request '" first-data "', replying 'Hello world!'")
          (bs/snd! full "Hello world!" :close? true)
          (debug "High-performance fiber-blocking Server connection handle @ http[s]://localhost:8080: replied 'Hello world!', exiting")
          (utils/unrecord-fiber :main-server-handle (bs/ch-id full)))
        :else
        (do
          (debug "High-performance fiber-blocking Server connection handle @ [http[s]|ws[s]]://localhost:8080: unknown protocol '" proto "'!!! Doing nothing and exiting")
          (utils/unrecord-fiber :main-server-handle (bs/ch-id full)))))
    (catch Throwable t
      (do
        (fatal t "High-performance fiber/actor-based Server got exception, dying!")
        (utils/unrecord-fiber :main-server-handle (bs/ch-id full))
        (throw t)))))

(pc/defsfn server [client-boot-channel]
  (try
    (let
        [_ (debug "High-performance fiber-blocking Server: activating @ [http|ws]://localhost:8080")
         srv (bs/bind! "localhost" 8080)]
      (debug "High-performance fiber-blocking Server @ [http|ws]://localhost:8080 active, activating client by sending 'GO' over client-sync channel")
      (pc/snd client-boot-channel "GO")
      (debug "High-performance fiber-blocking Server @ [http|ws]://localhost:8080 active, initiating serving loop")
      (loop []
        (debug "High-performance fiber-blocking Server @ [http|ws]://localhost:8080: getting incoming connection")
        (let [handle (bs/listen! srv)]
					(if handle
						(do
							(debug "High-performance fiber-blocking Server @ [http|ws]://localhost:8080: got connection, handing it to the handler")
							(utils/record-fiber :main-server-handle (bs/ch-id handle) (pc/spawn-fiber server-connection-handler [handle srv]))
							(debug "High-performance fiber-blocking Server @ [http|ws]://localhost:8080: handed connection to the handler")
							(recur))
						(do
							(debug "High-performance fiber-blocking Server @ [http|ws]://localhost:8080: got nil, exiting serving loop")
							(utils/unrecord-fiber :main-server "server"))))))
    (catch Throwable t
      (do
        (fatal t "High-performance fiber-blocking Server got exception, dying!")
        (utils/unrecord-fiber :main-server "server")
        (throw t)))))

(pc/defsfn client [client-boot-channel]
  (try
    (let [_ (debug "High-performance fiber-blocking Client: waiting for 'GO' (server ready) on server-sync channel")
          wait-chan (pc/channel)]
      (debug "High-performance fiber-blocking Client: received '" (pc/rcv client-boot-channel) "'")
      (debug "High-performance fiber-blocking Client: GETting 'http://localhost:8080'")
      (debug "High-performance fiber-blocking Client @ http://localhost:8080: '" (bc/http-get "http://localhost:8080" {}) "' response received")
      (debug "High-performance fiber-blocking Client @ http://localhost:8080: exiting")
      (utils/unrecord-fiber :main-client "client")
      (let [_ (debug "High-performance fiber-blocking Client:, opening 'ws://localhost:8080'") ws1 (bc/ws-open! "ws://localhost:8080")]
        (debug "High-performance fiber-blocking Client @ ws://localhost:8080: websocket opened successfully")
        (debug "High-performance fiber-blocking Client @ ws://localhost:8080: sending 'Yo!'")
        (bc/ws-snd! ws1 "Yo!")
        (debug "High-performance fiber-blocking Client @ ws://localhost:8080: 'Yo!' message sent, getting reply")
        (debug "High-performance fiber-blocking Client @ ws://localhost:8080: '" (bc/ws-rcv ws1) "' message received")
        (debug "High-performance fiber-blocking Client @ ws://localhost:8080: closing channel")
        (bc/ws-close! ws1)
        (debug "High-performance fiber-blocking Client @ ws://localhost:8080: closed channel")
        (debug "High-performance fiber-blocking Client @ ws://localhost:8080: exiting")
        (utils/unrecord-fiber :main-client "client")
        ; (debug "High-performance fiber-blocking Client @ ws://localhost:8080: sending '!!!CLOSE!!!' message")
        (comment let [_ (debug "High-performance fiber-blocking Client:, opening 'ws://localhost:8080'") ws2 (bc/ws-open! "ws://localhost:8080")]
          (debug "High-performance fiber-blocking Client @ ws://localhost:8080: sending '!!!CLOSE!!!' message")
          (bc/ws-snd! ws2 "!!!CLOSE!!!")
          (debug "High-performance fiber-blocking Client @ ws://localhost:8080: '!!!CLOSE!!!' message sent, exiting")
          (utils/unrecord-fiber :main-client "client"))))
    (catch Throwable t
      (do
        (fatal t "High-performance fiber-blocking Client got exception, dying!")
        (utils/unrecord-fiber :main-client "client")
        (throw t)))))

(defn -main []
  (do
    (utils/start-nstracker!)

    (timbre/set-config! [:timestamp-pattern] "yyyy-MMM-dd HH:mm:ss.SSS ZZ")
    (timbre/set-config!
      [:fmt-output-fn]
      (fn [{:keys [level throwable message timestamp hostname ns]}
           ;; Any extra appender-specific opts:
           & [{:keys [nofonts?] :as appender-fmt-output-opts}]]
        ;; <timestamp> <hostname> <thread> <fiber> <LEVEL> [<ns>] - <message> <throwable>
        (let [t (.hashCode (Thread/currentThread)) f (.hashCode (Strand/currentStrand))]
          (format "%s %s T(%s) F(%s) %s [%s] - %s%s"
                  timestamp hostname t (if (= t f) "NONE" f)
                  (-> level name str/upper-case) ns (or message "")
                  (or (timbre/stacktrace throwable "\n" (when nofonts? {})) "")))))

    (let
        [
         client-boot-channel (pc/channel)
         _ (debug "Spawning server fiber")
         server-fiber
         (pc/spawn-fiber server [client-boot-channel])
         _ (debug "Spawned server fiber")
         _ (debug "Spawning client fiber")
         client-fiber
         (pc/spawn-fiber client [client-boot-channel])
         _ (debug "Spawned client fiber")]
      (utils/record-fiber :main-server "server" server-fiber)
      (utils/record-fiber :main-client "client" client-fiber)
      (debug "Joining client")
      (pc/join client-fiber)
      (debug "Joined client")
      (debug "Joining server")
      (pc/join server-fiber)
      (debug "Joined server, exiting"))))