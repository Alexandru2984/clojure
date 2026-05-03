(ns eventpulse.mock
  (:require [clojure.string :as str])
  (:import [java.time Instant]))

(defn- at-seconds-ago [seconds]
  (str (.minusSeconds (Instant/now) seconds)))

(defn events []
  [{:id 9005
    :source "demo-webhook"
    :level "info"
    :type "deploy_finished"
    :message "Demo deployment finished successfully"
    :metadata {:project "sample-app" :duration_ms 1842}
    :received_at (at-seconds-ago 24)
    :client_id "demo"}
   {:id 9004
    :source "demo-worker"
    :level "warning"
    :type "queue_latency"
    :message "Demo job queue latency crossed the warning threshold"
    :metadata {:queue "emails" :latency_ms 820}
    :received_at (at-seconds-ago 95)
    :client_id "demo"}
   {:id 9003
    :source "demo-api"
    :level "error"
    :type "upstream_timeout"
    :message "Demo upstream request timed out"
    :metadata {:endpoint "/api/sample" :timeout_ms 5000}
    :received_at (at-seconds-ago 240)
    :client_id "demo"}
   {:id 9002
    :source "demo-monitor"
    :level "critical"
    :type "disk_pressure"
    :message "Demo disk pressure alert"
    :metadata {:mount "/" :used_percent 92}
    :received_at (at-seconds-ago 780)
    :client_id "demo"}
   {:id 9001
    :source "demo-scheduler"
    :level "debug"
    :type "heartbeat"
    :message "Demo scheduler heartbeat"
    :metadata {:interval_sec 60 :status "ok"}
    :received_at (at-seconds-ago 1420)
    :client_id "demo"}])

(defn- matches? [params event]
  (and (or (str/blank? (:source params)) (= (:source params) (:source event)))
       (or (str/blank? (:level params)) (= (:level params) (:level event)))
       (or (str/blank? (:type params)) (= (:type params) (:type event)))))

(defn list-events [{:keys [limit] :as params}]
  (let [limit (-> (or limit 100) int (max 1) (min 500))]
    (->> (events)
         (filter #(matches? params %))
         (take limit)
         vec)))

(defn- counts-by [key-fn rows]
  (->> rows
       (group-by key-fn)
       (map (fn [[name grouped]] {:name name :count (count grouped)}))
       (sort-by (juxt (comp - :count) :name))
       vec))

(defn stats []
  (let [rows (events)
        by-level (counts-by :level rows)
        last-at (:received_at (first rows))]
    {:total 128
     :last_5_minutes 7
     :last_15_minutes 19
     :last_60_minutes 64
     :by_level by-level
     :top_sources (take 8 (counts-by :source rows))
     :top_types (take 8 (counts-by :type rows))
     :last_event_at last-at
     :demo true}))
