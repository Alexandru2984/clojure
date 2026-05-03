(defproject eventpulse "0.1.0"
  :description "Clojure EventPulse - lightweight JSON event stream inspector"
  :url "https://clojure.micutu.com"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [ring/ring-core "1.15.4"]
                 [ring/ring-jetty-adapter "1.15.4"]
                 [cheshire "6.2.0"]
                 [com.github.seancorfield/next.jdbc "1.3.1093"]
                 [org.xerial/sqlite-jdbc "3.53.0.0"]
                 [org.slf4j/slf4j-api "2.0.17"]
                 [org.slf4j/slf4j-simple "2.0.17"]]
  :main ^:skip-aot eventpulse.main
  :uberjar-name "eventpulse-standalone.jar"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[lambdaisland/kaocha "1.91.1392"]]}}
  :aliases {"test" ["run" "-m" "kaocha.runner"]})
