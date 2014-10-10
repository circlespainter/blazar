(def date-formatter (new java.text.SimpleDateFormat "yyyyMMdd_HHmmss"))
(defn build [] (System/getenv "BUILD"))

(defproject blazar "0.1.1"

  :description
    "High-performance framework for I/O and real-time distributed HTTP based on Pulsar actors
       (including convenient synchronous and fiber-blocking library calls)"

  :url "https://github.com/circlespainter/blazar"

  ; TODO Check if it works for POM generation to have two under :licenses, Leiningen's sample doesn't list this case
  :licenses [{:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
             {:name "GNU Lesser General Public License - v 3" :url "http://www.gnu.org/licenses/lgpl.html"}]

  ; TODO Check if it works to have it outside of license(s), Leiningen's sample doesn't list this case
  :distribution :repo

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
     [com.taoensso/timbre "3.3.1"]
     [ns-tracker "0.2.2"]
     [co.paralleluniverse/pulsar "0.6.1"]
     [http-kit "2.1.16"]
     [stylefruits/gniazdo "0.3.0"]]

  :plugins
    [
     ; [lein-pprint "1.1.1"]
     ; [lein-marginalia "0.8.0"]
     ; [lein-html5-docs "2.2.0"]
     ; [codox "0.8.10"]
     ; [lein-midje "3.1.3"]
    ]

  ; :core.typed
  ;   {:check [blazar.http]}

  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo" :sign-releases false}]]

  :java-agents
    [[co.paralleluniverse/quasar-core "0.6.1"]]

  ;:main
  ;   blazar.examples.ring
  ;   blazar.examples.svr
  ;   blazar.examples.httpclt
  ;   blazar.examples.wsclt
)
