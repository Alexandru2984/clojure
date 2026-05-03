(ns eventpulse.config
  (:require [clojure.string :as str]))

(def default-env-path ".env")

(defn- parse-line [line]
  (let [line (str/trim line)]
    (when (and (seq line) (not (str/starts-with? line "#")) (str/includes? line "="))
      (let [[k v] (str/split line #"=" 2)]
        [(str/trim k) (str/trim v)]))))

(defn read-env-file
  ([] (read-env-file default-env-path))
  ([path]
   (try
     (->> (slurp path)
          str/split-lines
          (keep parse-line)
          (into {}))
     (catch java.io.FileNotFoundException _ {}))))

(defn- env-value [file-env key default]
  (or (System/getenv key) (get file-env key) default))

(defn- parse-int [value fallback]
  (try
    (Integer/parseInt (str value))
    (catch Exception _ fallback)))

(defn load-config
  ([] (load-config default-env-path))
  ([path]
   (let [file-env (read-env-file path)
         port (parse-int (env-value file-env "APP_PORT" "8120") 8120)]
     {:env-path path
      :host (env-value file-env "APP_HOST" "127.0.0.1")
      :port port
      :api-key (env-value file-env "APP_API_KEY" "")
      :admin-username (env-value file-env "ADMIN_USERNAME" "admin")
      :admin-password (env-value file-env "ADMIN_PASSWORD" "")
      :admin-password-hash (env-value file-env "ADMIN_PASSWORD_HASH" "")
      :session-secret (env-value file-env "ADMIN_SESSION_SECRET" "")
      :db-path (env-value file-env "DB_PATH" "data/eventpulse.sqlite3")})))
