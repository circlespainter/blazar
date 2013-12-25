(ns blazar.http.server-actors
  (:refer-clojure :exclude [promise await])
  (:require
    [org.httpkit.server :as h]
    [clojure.core.match :as m]
    [co.paralleluniverse.pulsar.core :as pc]
    [co.paralleluniverse.pulsar.actors :as pa])
  (:import
    (co.paralleluniverse.actors ActorRef)
    (clojure.lang IPersistentMap)))

(defn bind [^ActorRef server ^String hostname ^Integer port]
  "Fiber-blocking functions API: blocks the current fiber until it gets an HTTP message"
  (pa/call! server {:bind {:hostname hostname :port port}}))

(defn get-data [^ActorRef server]
  "Fiber-blocking functions API: blocks the current fiber until it gets an HTTP message"
  (pa/call! server :get-data))

(defn send-data
  "Fiber-blocking functions API: send data to a given server connection and optionally close the connection"
  [^ActorRef server ^ActorRef to data ^Boolean close]
  (pa/call! server {:send-data data :to to :close close}))

; Handy forward-declarations
(declare create-server server-factory-actor factory server-actor handler
         close-handler-actor send-handler-actor receive-handler-actor)

(defn create-server []
  "Server actor + fiber-blocking functions API: returns, building it beforehand if needed, the server actor"
  (let
      [term #(pa/! @pa/state :stop)
       server-type
       (reify pa/Server
         (init [_])
         (terminate [_ _]
           (do
             (println "Server terminating; asking for termination")
             (term)
             (println "Server terminating; termination asked, goodbye!")))
         (handle-info [_ msg]
           (m/match [msg]
                    [{:bound _}]
                    (println "Server binding " @pa/state ": received bound confirmation '" msg "'")))
         (handle-call [self from id msg]
           (m/match
             [msg]
             [:stop]
             (let
                 [_ (println "Server binding " @pa/state ": :stop received")
                  sr (pa/shutdown!)]
               (println "Server binding " @pa/state ": shutdown called, result: '" sr "'")
               sr)
             [:get-data]
             (let
                 [_ (println "Server binding " @pa/state ": receiving data")
                  d (pa/receive [m] {:data _} m)]
               (println "Server binding " @pa/state ": received data '" d "'")
               d)
             [{:bind {:hostname hostname :port port}}] ; Receive and store :bound response
             (let [sa (server-factory-actor @pa/self)]
               (println "Server binding @" hostname ":" port "; asking for creation to factory")
               (pa/! sa {:bind {:hostname hostname :port port}})
               (println "Server binding @" hostname ":" port "; creation asked")
               (pa/set-state! (pa/receive))
               (println "Server binding @" hostname ":" port "; got creation response, creation complete, returning 'true'")
               true)
             [{:send-data data :to send-handler :close close}]
             (do
               (println "Server binding " @pa/state ": asked to send '" data "' w/close flag " close)
               (pa/! send-handler {:data data :close close})
               (println "Server binding " @pa/state ": sent '" data "' w/close flag " close)
               true)
             :else
             (do
               (println "Server binding " @pa/state ": unknown data '" msg "' received")
               true))))
       _ (println "Server initalizing; type reified")
       generated-server (pa/gen-server server-type)
       _ (println "Server initalizing; generated server")
       server-actor (pa/spawn generated-server)]
    (println "Server initalizing; spawned instance")
    server-actor))

(defn server-factory-actor
  "Plain actor API: returns, building it beforehand if needed, the server factory singleton actor"
  [^ActorRef counterpart] ; Factory actor
  (let
      [_ (println "Server factory singleton is: '" @factory "'")
       ret
       (reset!
         factory
         (or
           @factory
           (do
             (println "Server factory creation: creating singleton")
             (pa/spawn
               #(try
                 (do
                   (println "Server factory: entering receive loop")
                   (loop []
                     (println "Server factory: entered receive loop")
                     (pa/receive
                       [x]
                       {:bind {:hostname hostname :port port}}
                       (do
                         (println "Server factory: received creation request for '" hostname ":" port "'")
                         (pa/! counterpart (server-actor @pa/self counterpart hostname port nil))
                         (println "Server factory: sent back server reference"))
                       :else (println "Server factory: received unknown message: '" x "'"))
                     (recur)))
                 (catch Throwable t (do (.printStackTrace t) (println "Server factory: got exception '" t "', dying!") (throw t))))))))]
    (println "Created server factory actor")
    ret))

(def ^{:private true :doc "Atom storing the server factory singleton actor"} factory (atom nil))

(defn- server-actor
  "Returns the server manager actor"
  [^ActorRef factory ^ActorRef counterpart ^String hostname ^Integer port wrapping] ; Server actor
  (pa/spawn
    #(try
      (let [ring-handler (partial handler @pa/self counterpart)]
        (println "Server instance: received creation request for '" hostname ":" port "', launching http-kit and putting function for later stop in actor's state")
        (pa/set-state!
          (h/run-server
            ((if (ifn? wrapping) wrapping identity) ring-handler)
            {:ip (or hostname "0.0.0.0") :port (or port 8080)}))
        (println "Server instance: received creation request for '" hostname ":" port ", launched, sending bound feedback message")
        (pa/! counterpart {:bound {:hostname hostname :port port}})
        (println "Server instance: received creation request for '" hostname ":" port ", listening for stop message")
        (pa/receive
          :stop
          (let [p (pc/promise)]
            (println "Server instance: received stop request, async'ly stopping")
            (pc/spawn-thread (fn [] (deliver p (@pa/state))))
            @p)))
      (catch Throwable t (do (.printStackTrace t) (println "Server instance: got exception '" t "', dying!") (throw t))))))

(defn- handler
  "Returns ring handler; it will just spawn three async handler actors"
  [^ActorRef server ^ActorRef counterpart req]
  (do
    (println "Server HTTP handler: invoked, getting channel from request")
    (h/with-channel
      req ch
      (let
          [_ (println "Server HTTP handler: received contact, request '" req "', websocket " (h/websocket? ch))
           send-handler (send-handler-actor ch)
           _ (println "Server HTTP handler: created send-handler actor: " send-handler)
           receive-handler (receive-handler-actor send-handler counterpart ch)
           _ (h/on-receive
               ch
               #(do
                 (println "Server HTTP handler callback: received data '" % "', forwarding to handling actor")
                 (pa/! receive-handler {:data % :websocket? (h/websocket? ch)})))
           _ (println "Server HTTP handler: created receive-handler actor (and registered callback): " receive-handler)
           close-handler (close-handler-actor counterpart ch)
           _ (println "Server HTTP handler: created close-handler actor: " close-handler)]
        ; Setter-style callback so I can't use await and I have to stay async ;(
        (pa/link! close-handler receive-handler)
        (pa/link! close-handler send-handler)))))

(defn- receive-handler-actor
  "Data reception (request or websocket) receive-only actor"
  [^ActorRef send-handler ^ActorRef counterpart ch]
  (pa/spawn
    :mailbox-size 0
    :overflow-policy :drop
    #(try
      (loop []
        (println "Server receive handler entering loop: waiting for HTTP data message")
        (pa/receive
          {:data d :websocket? ws}
          (do(println "Server receive handler: received HTTP data message '" d "' with websocket? '" ws "', forwarding")
             (pa/!
               counterpart
               {:data d :websocket? ws :reply-to send-handler})
             (println "Server receive handler: forwarded HTTP data")))
        (recur))
      (catch Throwable t (do (.printStackTrace t) (println "Server receive handler: got exception '" t "', dying!") (throw t))))))

(defn- close-handler-actor
  "Connection closing receive-only actor"
  [^ActorRef counterpart ch]
  (pa/spawn
    :trap true
    :mailbox-size 0
    :overflow-policy :drop
    #(try
      (do
        (println "Server close handler: waiting for HTTP close event")
        (let [d (pc/await h/on-close ch)]
          (println "Server receive handler: received HTTP close event, forwarding"))
        (pa/! counterpart {:closed @pa/self})
        (println "Server receive handler: forwarded HTTP close event"))
      (catch Throwable t (do (.printStackTrace t) (println "Server close handler: got exception '" t "', dying!") (throw t))))))

(defn- send-handler-actor
  "Data sending (request or websocket) send-only actor"
  [ch]
  (pa/spawn
    #(try
      (loop []
        (println "Server send handler entering loop: waiting for data to send back")
        (pa/receive
          [msg]
          {:data data :close close}
          (do
            (println "Server send handler: got data '" data "' and close toggle '" close "', sending back via HTTP")
            (h/send! ch data close)
            (println "Server send handler: sent back")
            (if (not close) (do (println "Server send handler: not closing yet, looping back") (recur))))
          :else
          (println "Server send handler: got unknown request '" msg "'")))
      (catch Throwable t (do (.printStackTrace t) (println "Server send handler: got exception '" t "', dying!") (throw t))))))