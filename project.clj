(def date-formatter (new java.text.SimpleDateFormat "yyyyMMdd_HHmmss"))
(defn build [] (System/getenv "BUILD"))

(defproject
  com.dreamtimecircles/blazar "0.1.0-SNAPSHOT"

  :description
    "High-performance framework for I/O and real-time distributed HTTP based on Pulsar actors
       (including convenient synchronous and fiber-blocking library calls)"

  :profiles {
    :dev {
      :resource-paths ["resources" "profiles/dev/resources"]
      :source-paths ["src" "profiles/dev/src"]
      :test-paths ["test" "profiles/dev/test"]
      :jvm-opts
      ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000"]}

    :prod {
      :resource-paths ["resources" "profiles/prod/resources"]
      :source-paths ["src" "profiles/prod/src"]
      :test-paths ["test" "profiles/prod/test"]
      :jvm-opts
      ["-server"
       "-XX:+UseConcMarkSweepGC"
       "-XX:+TieredCompilation"]}}

  :dependencies
    [[org.clojure/clojure "1.6.0"]
     [com.taoensso/timbre "3.2.1"]
     [ns-tracker "0.2.2"]
     ; [org.clojure/core.match "0.2.0"] ; Implicit
     ; [org.clojure/core.typed "0.2.17"] ; Implicit
     ; [co.paralleluniverse/quasar-core "0.4.0"] ; Implicit
     [co.paralleluniverse/pulsar "0.5.0"]
     [http-kit "2.1.16"] ; For http + websocket server support and http client support
     [stylefruits/gniazdo "0.2.1"]]    ; For websocket client support

  :plugins
    [[lein-clojars "0.9.1"]
     [lein-webrepl "0.1.0-SNAPSHOT"]
     [lein-marginalia "0.7.1"]
     [lein-pprint "1.1.1"]
     [codox "0.6.7"]
     [lein-bin "0.3.4"]
     [lein-servlet "0.3.0"]
     [lein-ring "0.8.10"]
     [lein-tar "2.0.0"]
     [lein-html5-docs "2.0.2"]
     [lein-licenses "0.1.1"]
     [lein-sub "0.3.0"]
     [lein-ancient "0.5.4"]
     [lein-nevam "0.1.2"]
     [lein-localrepo "0.5.3"]
     [lein-exec "0.3.2"]
     [lein-clean-m2 "0.1.2"]
     [lein-cljsbuild "1.0.2"]
     [lein-droid "0.2.2"]
     [lein-deps-tree "0.1.2"]
     [lein-environ "0.4.0"]
     [lein-typed "0.3.3"]
     [lein-midje "3.0.0"]]

  ; :core.typed
  ;   {:check [blazar.http]}

  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}

  :java-agents
    [[co.paralleluniverse/quasar-core "0.5.0"]]

  :main
    blazar.examples.svr
  ; blazar.examples.httpclt
  ; blazar.examples.wsclt
)
