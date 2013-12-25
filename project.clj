(def date-formatter (new java.text.SimpleDateFormat "yyyyMMdd_HHmmss"))
(defn build [] (System/getenv "BUILD"))

(defproject
  com.dreamtimecircles/blazar "0.1.0-SNAPSHOT"
  :description
  "High-performance framework for I/O and real-time distributed HTTP based on Pulsar actors
     (including convenient synchronous and fiber-blocking library calls)"

  :profiles
  {
    :dev
    {
      :resource-paths ["resources" "profiles/dev/resources"]
      :source-paths ["src" "profiles/dev/src"]
      :test-paths ["test" "profiles/dev/test"]
      :jvm-opts
      ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000"]}

    :prod
    {
      :resource-paths ["resources" "profiles/prod/resources"]
      :source-paths ["src" "profiles/prod/src"]
      :test-paths ["test" "profiles/prod/test"]
      :jvm-opts
      ["-server"
       "-XX:+UseConcMarkSweepGC"
       "-XX:+TieredCompilation"]}}

  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [com.taoensso/timbre "3.0.0-RC2"]
   [ns-tracker "0.2.1"]
   ; [org.clojure/core.match "0.2.0"] ; Implicit
   ; [org.clojure/core.typed "0.2.17"] ; Implicit
   ; [co.paralleluniverse/quasar-core "0.4.0-SNAPSHOT"] ; Implicit
   [co.paralleluniverse/pulsar "0.4.0-SNAPSHOT"]
   [http-kit "2.1.13"] ; For http + websocket server support and http client support
   [aleph "0.3.0"]]    ; For websocket client support

  :plugins
  [[lein-clojars "0.9.1"]
   [lein-webrepl "0.1.0-SNAPSHOT"]
   [lein-marginalia "0.7.1"]
   [lein-pprint "1.1.1"]
   [codox "0.6.4"]
   [lein-bin "0.2.0"]
   [lein-servlet "0.2.0"]
   [lein-ring "0.8.5"]
   [lein-tar "2.0.0"]
   [lein-html5-docs "2.0.0"]
   [lein-embongo "0.2.1"]
   [lein-licenses "0.1.1"]
   [lein-beanstalk "0.2.7"]
   [lein-sub "0.2.4"]
   [lein-outdated "1.0.1"]
   [lein-nevam "0.1.2"]
   [lein-localrepo "0.4.1"]
   [lein-exec "0.3.0"]
   [lein-clean-m2 "0.1.2"]
   [lein-cljsbuild "0.3.2"]
   [lein-droid "0.1.0-preview5"]
   [lein-deps-tree "0.1.2"]
   [lein-environ "0.4.0"]
   [lein-typed "0.3.1"]]

  ; :core.typed
  ; {:check [blazar.http]}

  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}


  :java-agents
  [[co.paralleluniverse/quasar-core "0.4.0-SNAPSHOT"]]

  :main main
)
