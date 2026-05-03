(ns eventpulse.db
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [java.time Instant]))

(defonce datasource (atom nil))

(defn- builder []
  {:builder-fn rs/as-unqualified-lower-maps})

(defn- ensure-parent-dir! [path]
  (when-let [parent (.getParentFile (io/file path))]
    (.mkdirs parent)))

(defn init! [db-path]
  (ensure-parent-dir! db-path)
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
    (reset! datasource ds)
    (jdbc/execute! ds ["PRAGMA journal_mode=WAL"])
    (jdbc/execute! ds ["PRAGMA busy_timeout=5000"])
    (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS events (
                         id INTEGER PRIMARY KEY AUTOINCREMENT,
                         source TEXT NOT NULL,
                         level TEXT NOT NULL,
                         type TEXT NOT NULL,
                         message TEXT NOT NULL,
                         metadata TEXT NOT NULL,
                         received_at TEXT NOT NULL,
                         client_id TEXT
                       )"])
    (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_events_received_at ON events(received_at DESC)"])
    (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_events_level ON events(level)"])
    (jdbc/execute! ds ["CREATE INDEX IF NOT EXISTS idx_events_source ON events(source)"])
    ds))

(defn- ds []
  (or @datasource (throw (ex-info "Database is not initialized" {}))))

(defn- row->event [row]
  (when row
    (-> row
        (update :metadata #(try (json/parse-string % true)
                                (catch Exception _ {}))))))

(defn cleanup! []
  (jdbc/execute! (ds) ["DELETE FROM events
                       WHERE id NOT IN (
                         SELECT id FROM events ORDER BY id DESC LIMIT 5000
                       )"]))

(defn insert-event! [{:keys [source level type message metadata client-id]}]
  (let [received-at (str (Instant/now))]
    (let [stored (jdbc/with-transaction [tx (ds)]
                   (jdbc/execute! tx ["INSERT INTO events
                                      (source, level, type, message, metadata, received_at, client_id)
                                      VALUES (?, ?, ?, ?, ?, ?, ?)"
                                      source level type message (json/generate-string metadata) received-at client-id])
                   (let [id (:id (jdbc/execute-one! tx ["SELECT last_insert_rowid() AS id"] (builder)))]
                     (row->event (jdbc/execute-one! tx ["SELECT * FROM events WHERE id = ?" id] (builder)))))]
      (cleanup!)
      stored)))

(defn list-events [{:keys [source level type limit]}]
  (let [limit (-> (or limit 100) int (max 1) (min 500))
        clauses (cond-> []
                  (seq source) (conj ["source = ?" source])
                  (seq level) (conj ["level = ?" level])
                  (seq type) (conj ["type = ?" type]))
        where (if (seq clauses)
                (str " WHERE " (clojure.string/join " AND " (map first clauses)))
                "")
        params (map second clauses)
        sql (str "SELECT * FROM events" where " ORDER BY id DESC LIMIT ?")]
    (mapv row->event (jdbc/execute! (ds) (vec (concat [sql] params [limit])) (builder)))))

(defn stats []
  (let [db (ds)
        now (Instant/now)
        since #(str (.minusSeconds now %))
        scalar (fn [sql & params]
                 (or (:value (jdbc/execute-one! db (vec (cons sql params)) (builder))) 0))
        grouped (fn [column limit]
                  (jdbc/execute! db [(str "SELECT " column " AS name, COUNT(*) AS count
                                           FROM events GROUP BY " column "
                                           ORDER BY count DESC, name ASC LIMIT ?")
                                     limit]
                                 (builder)))
        by-level (jdbc/execute! db ["SELECT level AS name, COUNT(*) AS count
                                     FROM events GROUP BY level ORDER BY count DESC"]
                                (builder))
        last-event (jdbc/execute-one! db ["SELECT received_at FROM events ORDER BY id DESC LIMIT 1"] (builder))]
    {:total (scalar "SELECT COUNT(*) AS value FROM events")
     :last_5_minutes (scalar "SELECT COUNT(*) AS value FROM events WHERE received_at >= ?" (since 300))
     :last_15_minutes (scalar "SELECT COUNT(*) AS value FROM events WHERE received_at >= ?" (since 900))
     :last_60_minutes (scalar "SELECT COUNT(*) AS value FROM events WHERE received_at >= ?" (since 3600))
     :by_level by-level
     :top_sources (grouped "source" 8)
     :top_types (grouped "type" 8)
     :last_event_at (:received_at last-event)}))
