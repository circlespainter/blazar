(ns blazar.http.client
  (:refer-clojure :exclude [promise await])
  (:require
    [co.paralleluniverse.pulsar.core :as pc]
    [org.httpkit.client :as hc]
    [aleph.http.websocket :as ahw]
    [lamina.core :as lc]
    [lamina.core.channel :as lcc]))

(set! *warn-on-reflection* true)

(defmacro ^:private deffiberreq [n method]
  `(pc/defsfn ~n [^String ~'url ~'opts]
    (pc/await ~(symbol "hc" (name method)) ~'url ~'opts)))

(deffiberreq http-get :get)
(deffiberreq http-delete! :delete)
(deffiberreq http-head :head)
(deffiberreq http-post! :post)
(deffiberreq http-put! :put)
(deffiberreq http-options :options)
(deffiberreq http-patch! :patch)

(pc/defsfn ^:private on-realized-onecb [lamina-promise cb]
  (lc/on-realized lamina-promise cb cb))

(pc/defsfn ws-open! [url]
  (pc/await on-realized-onecb (ahw/websocket-client {:url url}))) ; "ws://echo.websocket.org:80"

(pc/defsfn ws-rcv [ch]
  (pc/await on-realized-onecb (lcc/read-channel ch)))

(defn ws-snd! [ch msg]
  (lc/enqueue ch msg))

(defn ws-close! [ch]
  (lc/close ch))
