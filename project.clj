(defproject eventpulse "0.1.0"
  :description "Clojure EventPulse - lightweight JSON event stream inspector"
  :url "https://clojure.micutu.com"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-core "1.12.2"]
                 [ring/ring-jetty-adapter "1.12.2"]
                 [cheshire "5.13.0"]
                 [com.github.seancorfield/next.jdbc "1.3.955"]
                 [org.xerial/sqlite-jdbc "3.45.3.0"]
                 [org.slf4j/slf4j-api "2.0.13"]
                 [org.slf4j/slf4j-simple "2.0.13"]]
  :main ^:skip-aot eventpulse.main
  :uberjar-name "eventpulse-standalone.jar"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[lambdaisland/kaocha "1.91.1392"]]}}
  :aliases {"test" ["run" "-m" "kaocha.runner"]})
