(ns blazar.http.server
  (:import
    (co.paralleluniverse.strands Strand)
    (java.util.concurrent TimeUnit))
  (:refer-clojure :exclude [promise await])
  (:require
    [clojure.core.match :as m]
    [taoensso.timbre :as timbre
      :refer (log trace  debug  info  warn  error  fatal  report
              logf tracef debugf infof warnf errorf fatalf reportf
              spy logged-future with-log-level)]
    [org.httpkit.server :as h]
    [co.paralleluniverse.pulsar.core :as pc]
    [co.paralleluniverse.pulsar.actors :as pa]
    [blazar.utils :as utils
      :refer (record-fiber unrecord-fiber)]))

(def ^:private open? #(not (pc/closed? %)))
(def ^:private close-if-open #(if (open? %) (pc/close! %)))
(def ^:private comms-open? #(every? open? %))
(def ^:private close-comms #(doseq [x %] (if (open? x) (pc/close! x))))

(defn ch-id [ch]
  (m/match [ch]
  [[_ {:ch-id id}]] id
  [ch] (.hashCode ch)))

(defn- spawn-temp-httpresponse-fiber [id httpkit-data-channel req]
  (record-fiber :api-server-temp-http-response id (pc/spawn-fiber
    #(try
      (do
        (debug "[Blazar server API: temp fiber for http-kit's plain HTTP handling] Forwarding request from connection '" id "' to managing fiber")
        (pc/snd httpkit-data-channel req)
        (debug "[Blazar server API: temp fiber for http-kit's plain HTTP handling] Forwarded to managing fiber (connection '" id "'), exiting")
        (unrecord-fiber :api-server-temp-http-response id))
      (catch Throwable t
        (do
          (fatal t "[Blazar server API: temp fiber for http-kit's plain HTTP handling] Got exception (connection '" id "'), dying!")
          (unrecord-fiber :api-server-temp-http-response id)
          (throw t)))))))

(defn- spawn-server-sending-fiber [ch public-channel-snd comms]
  (let [id (ch-id ch)]
    (record-fiber :api-server-sending id (pc/spawn-fiber
      #(try
        (loop []
          (debug "[Blazar server API: server sending fiber] Entered message handling loop for connection '" id "'")
          (if-let [d (pc/rcv public-channel-snd)]
            (do
              (debug "[Blazar server API: server sending fiber] Received server data '" d "' on connection '" id "', forwarding to client (HTTP)")
              (h/send! ch d)
              (debug "[Blazar server API: server sending fiber] Received server data '" d "' on connection '" id "', forwarded to client (HTTP); looping back")
              (recur))
            (do
              (debug "[Blazar server API: server sending fiber] Received nil on connection '" id "', closing")
              (close-comms comms)
              (debug "[Blazar server API: server sending fiber] Received nil on connection '" id "', closed and now exiting")
              (unrecord-fiber :api-server-sending id))))
        (catch Throwable t
          (do
            (fatal t "[Blazar server API: 'on-receive'-managing fiber] Got exception (connection '" id "'), dying!")
            (unrecord-fiber :api-server-sending id)
            (throw t))))))))

(defn- spawn-onreceivemanaging-fiber [proto ch id httpkit-data-channel public-channel-rcv comms]
  (record-fiber :api-server-on-receive-managing id (pc/spawn-fiber
    #(try
      (do
        (loop []
          (debug "[Blazar server API: 'on-receive'-managing fiber] Entered message handling loop (connection '" id "')")
          (if-let [d (pc/rcv httpkit-data-channel)]
            (do
              (debug "[Blazar server API: 'on-receive'-managing fiber] Received client (HTTP) data '" d "' (connection '" id "'), forwarding to handle")
              (pc/snd public-channel-rcv {(if (= proto :ws) :ws-text :http-req) d})
              (debug "[Blazar server API: 'on-receive'-managing fiber] Received client (HTTP) data '" d "' (connection '" id "'), forwarded to handle; looping back")
              (recur))
            (do
              (debug "[Blazar server API: 'on-receive'-managing fiber] Received nil (connection '" id "'), closing internal channels")
              (close-comms (concat [httpkit-data-channel public-channel-rcv] comms))
              (debug "[Blazar server API: 'on-receive'-managing fiber] Received nil (connection '" id "'), now closing http-kit's handle")
              (h/close ch)
              (debug "[Blazar server API: 'on-receive'-managing fiber] Received nil (connection '" id "'), closed and now exiting")
              (unrecord-fiber :api-server-on-receive-managing id)))))
      (catch Throwable t
        (do
          (fatal t "[Blazar server API: 'on-receive'-managing fiber] Got exception (connection '" id "'), dying!")
          (unrecord-fiber :api-server-on-receive-managing id)
          (throw t)))))))

(defn- spawn-onclosemanaging-fiber [ch id httpkit-ws-close-channel httpkit-data-channel public-channel-rcv comms]
  (record-fiber :api-server-on-close-managing id (pc/spawn-fiber
    #(try
      (do
        (debug "[Blazar server API: 'on-close'-managing fiber] (connection '" id "')")
        (when-let [d (pc/rcv httpkit-ws-close-channel)]
          (do
            (debug "[Blazar server API: 'on-close'-managing fiber] Received closing reason '" d "' (connection '" id "'), forwarding to handle")
            (pc/snd public-channel-rcv {:ws-close d})
            (debug "[Blazar server API: 'on-close'-managing fiber] Received closing reason '" d "' (connection '" id "'), forwarded to handle and now closing internal channels")
            (close-comms (concat [httpkit-ws-close-channel httpkit-data-channel public-channel-rcv] comms))
            (debug "[Blazar server API: 'on-close'-managing fiber] Received closing reason '" d "' (connection '" id "'), now closing http-kit's handle")
            (h/close ch)
            (debug "[Blazar server API: 'on-close'-managing fiber] Received closing reason '" d "' (connection '" id "'), closed and now exiting")
            (unrecord-fiber :api-server-on-close-managing id))))
      (catch Throwable t
        (do
          (fatal t "[Blazar server API: 'on-close'-managing fiber] Got exception (connection '" id "'), dying!")
          (unrecord-fiber :api-server-on-close-managing id)
          (throw t)))))))

(defn- send-handle [ch [_ {r :rcv id :ch-id} :as h]]
  (do
    (debug "[Blazar server API: http-kit's ring handler] Spawning connection-returning fiber for '" id "'")
    (record-fiber :api-server-temp-handle-returning id (pc/spawn-fiber
      #(try
        (do
          (debug "[Blazar server API: http-kit's temp connection-returning fiber] Sending connection handle '" id "' over return channel")
          (pc/snd ch h)
          (debug "[Blazar server API: http-kit's temp connection-returning fiber] Sent connection handle '" id "' over return channel, exiting")
          (unrecord-fiber :api-server-temp-handle-returning id))
        (catch Throwable t
          (do
            (fatal t "[Blazar server API: http-kit's temp connection-returning fiber] Got exception (connection '" id "'), dying!")
            (unrecord-fiber :api-server-temp-handle-returning id)
            (throw t))))))))

(defn- handler
  "Returns the Blazar ring handler"
  [handler-fiber-channels-return-channel req]
  (let [httpkit-data-channel (pc/channel -1) httpkit-ws-close-channel (pc/channel 1)]
    (debug "[Blazar server API: http-kit's ring handler] Got incoming request")
    (h/with-channel
      req ch
      (let [ch-id (ch-id ch)]
        (if (h/websocket? ch)
          (do
            (h/on-receive ch #(when % (pc/snd httpkit-data-channel %)))
            (h/on-close ch #(when % (pc/snd httpkit-ws-close-channel %))))
          (spawn-temp-httpresponse-fiber ch-id httpkit-data-channel req))
        (let
          [proto (if (h/websocket? ch) :ws :http)
           public-channel-snd (pc/channel)
           public-channel-rcv (pc/channel)
           comms [httpkit-data-channel public-channel-snd public-channel-rcv]
           handle {:ch-id ch-id :snd public-channel-snd :rcv public-channel-rcv}]
          (spawn-server-sending-fiber ch public-channel-snd comms)
          (spawn-onreceivemanaging-fiber proto ch ch-id httpkit-data-channel public-channel-rcv comms)
          (when (= proto :ws) (spawn-onclosemanaging-fiber ch ch-id httpkit-ws-close-channel httpkit-data-channel public-channel-rcv comms))
          (send-handle handler-fiber-channels-return-channel [proto handle]))))))

(defn closed? [& args]
  "API: checks if server and/or connection handles passed in are all closed"
  (every?
    (fn [handle]
      (m/match
        [handle]
        [[_ {:rcv r :snd s}]] (some #(pc/closed? %) [r s])
        [{:handler-fiber-channels-return-channel rc}] (pc/closed? rc)
        :else false))
    args))

(defn bind [& {:keys [ip port wrapping] :or {ip "0.0.0.0" port 8080 wrapping nil} :as args}]
  "API: creates an httpkit instance whose handler will communicate through Pulsar channels"
  (let [_ (debug "[Blazar server API] Binding based on '" args "', creating connection return channel")
        handler-fiber-channels-return-channel (pc/channel)
        f (h/run-server
            ((if (ifn? wrapping) wrapping identity) (partial handler handler-fiber-channels-return-channel))
            {:ip ip :port port})
        wrapped-f (fn [] (do (f)))
        ret {:handler-fiber-channels-return-channel handler-fiber-channels-return-channel :close-function wrapped-f}]
    (debug "[Blazar server API] Listening based on '" args "', returning")
    ret))

(defn unbind [{c :handler-fiber-channels-return-channel f :close-function :as full}]
  "API: destroy an httpkit instance"
  (let
    [
      _ (debug "[Blazar server API] Closing connections return channel")
      _ (pc/close! c)
      _ (debug "[Blazar server API] Closed connections return channel, unlistening")
      ret (f)
      _ (debug "[Blazar server API] Unlistened")
    ]
    (debug "[Blazar server API] Returning '" ret "'")))

(pc/defsfn listen [{c :handler-fiber-channels-return-channel :as full} & {:keys [timeout timeout-unit] :or {timeout -1 timeout-unit (. TimeUnit SECONDS)}}]
  "Fiber-blocking API: blocks the calling fiber until it gets a new connection handle from
  a server (or nil if server closed)"
  (let
    [_ (debug "[Blazar server API] Getting connection from server")
     ret (cond (< timeout 0) (pc/rcv c) (= timeout 0) (pc/try-rcv c) (> timeout 0) (pc/rcv c timeout timeout-unit))]
    (debug "[Blazar server API] Got connection (or nil) from server: returning '" ret "'")
    ret))

(pc/defsfn close
  "Fiber-blocking API: closes a connection handle"
  [[_ {r :rcv s :snd id :ch-id}]]
    (do
      (debug "[Blazar server API] Closing websocket handle '" id "'")
      (pc/close! r)
      (pc/close! s)
      (debug "[Blazar server API] Closed websocket handle '" id "'")))

(pc/defsfn rcv [[_ {r :rcv id :ch-id} :as full] & {:keys [close? timeout timeout-unit] :or {close? false timeout -1 timeout-unit (. TimeUnit SECONDS)}}]
  "Fiber-blocking API: blocks the calling fiber until it gets an HTTP message from
  a connection handle (or nil if handle closed)"
  (let
      [_ (debug "[Blazar server API] Getting data from connection '" id "'")
       ret (cond (< timeout 0) (pc/rcv r) (= timeout 0) (pc/try-rcv r) (> timeout 0) (pc/rcv r timeout timeout-unit))]
    (if close?
      (do
        (debug "[Blazar server API] Got data '" ret "' from connection '" id "', closing as asked")
        (close full)
        (debug "[Blazar server API] Got data '" ret "' from connection '" id "', closed"))
      (debug "[Blazar server API] Got data '" ret "' from connection '" id "', NOT closing"))
    (debug "[Blazar server API] Got data '" ret "' from connection '" id "', returning it")
    ret))

(pc/defsfn snd [[_ {s :snd  id :ch-id} :as full] data & {:keys [close? try?] :or {close? false try? false}}]
  "Fiber-blocking API: sends data to a given connection handle"
    (if (not (pc/closed? s))
      (do
        (debug "[Blazar server API] Sending data '" data "' over open connection '" id "'")
        (let [ret (if try? (pc/try-snd s data) (pc/snd s data))]
          (if close?
            (do
              (debug "[Blazar server API] Sent data '" data "' over open connection '" id "', closing as asked")
              (close full)
              (debug "[Blazar server API] Sent data '" data "' over open connection '" id "', closed"))
            (debug "[Blazar server API] Sent data '" data "' over open connection '" id "', asked not to close"))
          ret))
      (do
        (debug "[Blazar server API] Sending data '" data "' over open connection '" id "': already closed, not sending")
        false)))