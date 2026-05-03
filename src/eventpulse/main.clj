(ns eventpulse.main
  (:gen-class)
  (:require [eventpulse.config :as config]
            [eventpulse.db :as db]
            [eventpulse.routes :as routes]
            [ring.adapter.jetty :as jetty]))

(defonce server (atom nil))

(defn stop! []
  (when-let [s @server]
    (.stop s)
    (reset! server nil)))

(defn start! []
  (let [cfg (config/load-config)]
    (when-not (seq (:api-key cfg))
      (throw (ex-info "APP_API_KEY is required" {})))
    (when-not (or (seq (:admin-password-hash cfg))
                  (seq (:admin-password cfg)))
      (throw (ex-info "ADMIN_PASSWORD_HASH is required" {})))
    (db/init! (:db-path cfg))
    (reset! server
            (jetty/run-jetty (routes/app cfg)
                             {:host (:host cfg)
                              :port (:port cfg)
                              :join? false
                              :send-server-version? false}))
    (println (str "Clojure EventPulse listening on " (:host cfg) ":" (:port cfg)))
    cfg))

(defn -main [& _args]
  (start!)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop!))
  @(promise))
