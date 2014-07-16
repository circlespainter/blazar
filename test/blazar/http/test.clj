(ns blazar.http.test
	(:refer-clojure :exclude [promise await])
	(:use
		clojure.test)
	(:require
		[clojure.stacktrace :as stcktrc]
		[clojure.string :as str]
		[blazar.http.server :as bs]
		[blazar.http.client :as bc]
		[co.paralleluniverse.pulsar.core :as pc]
		[taoensso.timbre :as timbre]))

(timbre/set-level! :info)

(deftest test-all []
	(pc/defsfn http-req-res []
		(let [
			client-boot-channel (pc/channel 1)
			server-fiber (pc/spawn-fiber
				#(try (let
					[srv (bs/bind! "localhost" 8080)
					 _ (pc/snd client-boot-channel "GO")
					 handle (bs/listen! srv)
					 _ (bs/rcv handle)]
					(bs/snd! handle "Hello world!" :close? true))
					(catch Throwable t (stcktrc/print-cause-trace t))))
			client-fiber (pc/spawn-fiber
				#(try
					(pc/rcv client-boot-channel)
					(bc/http-get "http://localhost:8080" {})
					(catch Throwable t (stcktrc/print-cause-trace t))))]
			(pc/join server-fiber)
			(pc/join client-fiber)))
	(testing "HTTP GET req/res"
		(is (http-req-res) "Hello world!")))

(pc/suspendable! test-all)