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



(timbre/set-config! [:timestamp-pattern] "yyyy-MMM-dd HH:mm:ss.SSS ZZ")
(timbre/set-level! :info)



(deftest test-http []


	(pc/defsfn http-req-res []
		(let
			[
				synchronization-channel (pc/channel 1)

				server-fiber (pc/spawn-fiber
					#(try
						(let
							[srv (bs/bind! "localhost" 8080)
						 	_ (pc/snd synchronization-channel "GO")
						 	handle (bs/listen! srv)
						 	_ (bs/rcv handle)]
							(bs/snd! handle "Hello world!" :close? true)
							(pc/rcv synchronization-channel)
							(bs/unbind! srv))
						(catch Throwable t (stcktrc/print-cause-trace t) "KO")))

					client-fiber (pc/spawn-fiber
						#(try
							(pc/rcv synchronization-channel)
							(let [res (bc/http-get "http://localhost:8080")]
								(pc/snd synchronization-channel "DIE")
								(:body res))
							(catch Throwable t (stcktrc/print-cause-trace t) "KO")))]

			(pc/join server-fiber)
			(pc/join client-fiber)))


	(testing "HTTP GET req/res"
		(is (= (http-req-res) "Hello world!"))))



(deftest test-ws []


	(pc/defsfn ws-conversation-server-terminated []
		(let [
			synchronization-channel (pc/channel 1)

			server-fiber (pc/spawn-fiber
				#(try (let
					[srv (bs/bind! "localhost" 8080)
					 _ (pc/snd synchronization-channel "GO")
					 handle (bs/listen! srv)]
					 (println "SVR: got connection, sending Start")
					 (bs/snd! handle "Start")
					 (println "SVR: sent Start, receiving OK")
					 (bs/rcv handle)
					 (println "SVR: received OK, sending Step1")
					 (bs/snd! handle "Step1")
					 (println "SVR: sent Step1, receiving OK")
					 (bs/rcv handle)
					 (println "SVR: received OK, sending Step2")
					 (bs/snd! handle "Step2")
					 (println "SVR: sent Step2, receiving OK")
					 (bs/rcv handle)
					 (println "SVR: received OK, sending Closing and closing connection")
					 (bs/snd! handle "Closing" :close? true)
					 ; (bs/snd! handle "Closing")
					 ; (bs/close! handle)
					 (println "SVR: sent Closing and closed connection, waiting for client completion before shutting down")
					 (pc/rcv synchronization-channel)
					 (println "SVR: shutting down")
					 (bs/unbind! srv)
					 (println "SVR: shutdown complete, exiting"))
					(catch Throwable t (stcktrc/print-cause-trace t) "KO")))

			client-fiber (pc/spawn-fiber
				#(try
					(pc/rcv synchronization-channel)
					(let
							[
								_ (println "CLT: Connecting to ws://localhost:8080")
								skt (bc/ws-open! "ws://localhost:8080")
								_ (println "CLT: Connected to ws://localhost:8080, receiving")
							 	rcv1 (bc/ws-rcv skt)
								_ (println "CLT: Got" rcv1 ", sending OK")
								_ (bc/ws-snd! skt "OK")
								_ (println "CLT: sent OK, receiving")
								rcv2 (bc/ws-rcv skt)
								_ (println "CLT: Got" rcv2 ", sending OK")
								_ (bc/ws-snd! skt "OK")
								_ (println "CLT: sent OK, receiving")
								rcv3 (bc/ws-rcv skt)
								_ (println "CLT: Got" rcv3 ", sending OK")
								_ (bc/ws-snd! skt "OK")
								_ (println "CLT: sent OK, receiving last message")
								rcv4 (bc/ws-rcv skt)
								_ (println "CLT: Got" rcv4 ", receiving connection end")
								rcv5 (bc/ws-rcv skt)
							]
						(println "CLT: Got" rcv5 "connection end")
						(println "CLT: Signalling server to exit")
						(pc/snd synchronization-channel "DIE")
						(println "CLT: Signalled server to exit, checking values and computing OK/KO")
						(if
							(and
								(= rcv1 {:value "Start"})
								(= rcv2 {:value "Step1"})
								(= rcv3 {:value "Step2"})
								(= rcv4 {:value "Closing"})
								(:closed rcv5))
							"OK"
							"KO"))
					(catch Throwable t (stcktrc/print-cause-trace t) "KO")))]

			(pc/join server-fiber)
			(pc/join client-fiber)))


	(pc/defsfn ws-conversation-client-terminated []
		(let [
			synchronization-channel (pc/channel 1)

			server-fiber (pc/spawn-fiber
				#(try
					(let
						[
							srv (bs/bind! "localhost" 8080)
							_ (pc/snd synchronization-channel "GO")
							handle (bs/listen! srv)
						]
						(bs/snd! handle "Start")
						(bs/rcv handle)
						(bs/snd! handle "Step1")
						(bs/rcv handle)
						(bs/snd! handle "Step2")
						(bs/rcv handle)
						(bs/snd! handle "Close please!")
						(bs/rcv handle)
						(bs/unbind! srv))
					(catch Throwable t (stcktrc/print-cause-trace t) "KO")))

			client-fiber (pc/spawn-fiber
				#(try
					(pc/rcv synchronization-channel)
					(let
						[
							skt (bc/ws-open! "ws://localhost:8080")
							rcv1 (bc/ws-rcv skt)
							_ (bc/ws-snd! skt "OK")
							rcv2 (bc/ws-rcv skt)
							_ (bc/ws-snd! skt "OK")
							rcv3 (bc/ws-rcv skt)
							_ (bc/ws-snd! skt "OK")
							rcv4 (bc/ws-rcv skt)
						]
						(bc/ws-close! skt)
						(if
							(and
								(= rcv1 {:value "Start"})
								(= rcv2 {:value "Step1"})
								(= rcv3 {:value "Step2"})
								(= rcv4 {:value "Close please!"}))
							"OK"
							"KO"))
					(catch Throwable t (stcktrc/print-cause-trace t) "KO")))]
			(pc/join server-fiber)
			(pc/join client-fiber)))


	(comment (testing "WS conversation server-terminated"
		(is (= (ws-conversation-server-terminated) "OK"))))

	(testing "WS conversation client-terminated"
		(is (= (ws-conversation-client-terminated) "OK"))))


(pc/suspendable! test-http)
(pc/suspendable! test-ws)