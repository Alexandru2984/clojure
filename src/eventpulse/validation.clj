(ns eventpulse.validation
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(def allowed-levels #{"debug" "info" "warning" "error" "critical"})
(def allowed-keys #{:source :level :type :message :metadata})
(def required-keys [:source :level :type :message])

(def max-body-bytes 32768)
(def max-source-length 80)
(def max-type-length 80)
(def max-message-length 1000)
(def max-metadata-bytes 8192)

(defn- blank-string? [value]
  (or (not (string? value)) (str/blank? value)))

(defn- too-long? [value max-length]
  (> (count value) max-length))

(defn validate-event [payload]
  (let [errors (cond-> []
                 (not (map? payload))
                 (conj "Body must be a JSON object.")

                 (map? payload)
                 (into (for [k (keys payload)
                             :when (not (contains? allowed-keys k))]
                         (str "Unknown field: " (name k) ".")))

                 (and (map? payload) (some #(not (contains? payload %)) required-keys))
                 (into (for [k required-keys
                             :when (not (contains? payload k))]
                         (str "Missing required field: " (name k) "."))))]
    (if (seq errors)
      {:valid? false :errors errors}
      (let [{:keys [source level type message metadata]} payload
            metadata-json (when (contains? payload :metadata)
                            (json/generate-string metadata))
            field-errors (cond-> []
                           (blank-string? source)
                           (conj "source must be a non-empty string.")
                           (and (string? source) (too-long? source max-source-length))
                           (conj (str "source must be at most " max-source-length " characters."))
                           (blank-string? level)
                           (conj "level must be a non-empty string.")
                           (and (string? level) (not (contains? allowed-levels level)))
                           (conj "level must be one of debug, info, warning, error, critical.")
                           (blank-string? type)
                           (conj "type must be a non-empty string.")
                           (and (string? type) (too-long? type max-type-length))
                           (conj (str "type must be at most " max-type-length " characters."))
                           (blank-string? message)
                           (conj "message must be a non-empty string.")
                           (and (string? message) (too-long? message max-message-length))
                           (conj (str "message must be at most " max-message-length " characters."))
                           (and (contains? payload :metadata) (not (map? metadata)))
                           (conj "metadata must be a JSON object when provided.")
                           (and metadata-json (> (alength (.getBytes metadata-json "UTF-8")) max-metadata-bytes))
                           (conj (str "metadata must be at most " max-metadata-bytes " bytes.")))]
        (if (seq field-errors)
          {:valid? false :errors field-errors}
          {:valid? true
           :event {:source (str/trim source)
                   :level level
                   :type (str/trim type)
                   :message (str/trim message)
                   :metadata (or metadata {})}})))))
