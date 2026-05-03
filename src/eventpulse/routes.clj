(ns eventpulse.routes
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [eventpulse.db :as db]
            [eventpulse.mock :as mock]
            [eventpulse.sse :as sse]
            [eventpulse.validation :as validation]
            [eventpulse.views :as views]
            [ring.util.codec :as codec]
            [ring.util.response :as response])
  (:import [java.io ByteArrayOutputStream]
           [java.security MessageDigest]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(def security-headers
  {"X-Content-Type-Options" "nosniff"
   "X-Frame-Options" "DENY"
   "Referrer-Policy" "strict-origin-when-cross-origin"
   "Permissions-Policy" "geolocation=(), microphone=(), camera=()"})

(defn- json-response
  ([body] (json-response 200 body))
  ([status body]
   {:status status
    :headers (merge security-headers {"Content-Type" "application/json; charset=utf-8"})
    :body (json/generate-string body)}))

(defn- html-response [body]
  {:status 200
   :headers (merge security-headers {"Content-Type" "text/html; charset=utf-8"})
   :body body})

(defn- not-found []
  (json-response 404 {:error "Not found"}))

(defn- redirect-response [location]
  {:status 303
   :headers (merge security-headers {"Location" location})
   :body ""})

(defn- query-params [request]
  (codec/form-decode (or (:query-string request) "") "UTF-8"))

(defn- parse-limit [value]
  (when (seq value)
    (try
      (Integer/parseInt value)
      (catch Exception _ nil))))

(defn- read-limited-body [request]
  (let [content-length (some-> (get-in request [:headers "content-length"]) Long/parseLong)]
    (when (and content-length (> content-length validation/max-body-bytes))
      (throw (ex-info "Request body too large" {:status 413})))
    (let [body (:body request)
          buffer (byte-array 4096)
          out (ByteArrayOutputStream.)]
      (loop [total 0]
        (let [n (.read body buffer)]
          (when (pos? n)
            (let [new-total (+ total n)]
              (when (> new-total validation/max-body-bytes)
                (throw (ex-info "Request body too large" {:status 413})))
              (.write out buffer 0 n)
              (recur new-total)))))
      (.toString out "UTF-8"))))

(defn- parse-json-body [request]
  (try
    (json/parse-string (read-limited-body request) true)
    (catch clojure.lang.ExceptionInfo e
      (throw e))
    (catch Exception _
      (throw (ex-info "Malformed JSON body" {:status 400})))))

(defn- parse-form-body [request]
  (codec/form-decode (read-limited-body request) "UTF-8"))

(defn- sha256-prefix [value]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (or value "") "UTF-8"))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) digest)) 0 16)))

(defn- hmac-sha256 [secret value]
  (let [mac (Mac/getInstance "HmacSHA256")
        key (SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256")]
    (.init mac key)
    (apply str (map #(format "%02x" (bit-and % 0xff))
                    (.doFinal mac (.getBytes value "UTF-8"))))))

(defn- constant= [a b]
  (let [left (.getBytes (or a "") "UTF-8")
        right (.getBytes (or b "") "UTF-8")]
    (MessageDigest/isEqual left right)))

(defn- parse-cookies [request]
  (->> (str/split (or (get-in request [:headers "cookie"]) "") #";")
       (keep (fn [part]
               (let [[k v] (str/split (str/trim part) #"=" 2)]
                 (when (and (seq k) v)
                   [k v]))))
       (into {})))

(defn- session-token [config username]
  (str username ":" (hmac-sha256 (:session-secret config) username)))

(defn- authenticated? [request config]
  (let [token (get (parse-cookies request) "eventpulse_admin")
        username (:admin-username config)
        expected (session-token config username)]
    (and (seq (:session-secret config))
         (seq token)
         (constant= expected token))))

(defn- secure-request? [request]
  (or (= "https" (get-in request [:headers "x-forwarded-proto"]))
      (= :https (:scheme request))))

(defn- session-cookie [value max-age secure?]
  (str "eventpulse_admin=" value
       "; Path=/; Max-Age=" max-age
       "; HttpOnly"
       (when secure? "; Secure")
       "; SameSite=Lax"))

(defn- login-response [request config]
  (-> (redirect-response "/")
      (assoc-in [:headers "Set-Cookie"]
                (session-cookie (session-token config (:admin-username config))
                                28800
                                (secure-request? request)))))

(defn- logout-response [request]
  (-> (redirect-response "/")
      (assoc-in [:headers "Set-Cookie"] (session-cookie "expired" 0 (secure-request? request)))))

(defn- client-id [request]
  (let [forwarded (first (str/split (or (get-in request [:headers "x-forwarded-for"]) "") #","))
        remote (or (not-empty (str/trim forwarded)) (:remote-addr request) "unknown")]
    (str "sha256:" (sha256-prefix remote))))

(defn- authorized? [request config]
  (let [expected (:api-key config)
        provided (get-in request [:headers "x-api-key"])]
    (and (seq expected) (= expected provided))))

(defn- handle-post-event [request config]
  (if-not (authorized? request config)
    (json-response 401 {:error "Unauthorized"})
    (try
      (let [payload (parse-json-body request)
            result (validation/validate-event payload)]
        (if-not (:valid? result)
          (json-response 400 {:error "Invalid event" :details (:errors result)})
          (let [stored (db/insert-event! (assoc (:event result) :client-id (client-id request)))]
            (sse/broadcast! {:event "created" :data stored})
            (json-response 201 {:ok true :event stored}))))
      (catch clojure.lang.ExceptionInfo e
        (json-response (or (:status (ex-data e)) 400) {:error (.getMessage e)}))
      (catch Exception e
        (.println System/err (str "POST /api/events failed: " (.getMessage e)))
        (json-response 500 {:error "Internal server error"})))))

(defn- handle-list-events [request]
  (let [params (query-params request)
        level (get params "level")]
    (cond
      (and (seq level) (not (contains? validation/allowed-levels level)))
      (json-response 400 {:error "Invalid level filter"})

      :else
      (json-response {:events (db/list-events {:source (get params "source")
                                               :level level
                                               :type (get params "type")
                                               :limit (parse-limit (get params "limit"))})}))))

(defn- handle-mock-list-events [request]
  (let [params (query-params request)
        level (get params "level")]
    (cond
      (and (seq level) (not (contains? validation/allowed-levels level)))
      (json-response 400 {:error "Invalid level filter"})

      :else
      (json-response {:demo true
                      :events (mock/list-events {:source (get params "source")
                                                 :level level
                                                 :type (get params "type")
                                                 :limit (parse-limit (get params "limit"))})}))))

(defn- handle-login [request config]
  (let [params (parse-form-body request)
        username (get params "username")
        password (get params "password")]
    (if (and (constant= (:admin-username config) username)
             (constant= (:admin-password config) password)
             (seq (:admin-password config))
             (seq (:session-secret config)))
      (login-response request config)
      (html-response (views/login true)))))

(defn- static-response [uri]
  (when-let [content-type (case uri
                            "/styles.css" "text/css; charset=utf-8"
                            "/app.js" "application/javascript; charset=utf-8"
                            nil)]
    (some-> (response/resource-response (subs uri 1) {:root "public"})
            (update :headers merge security-headers {"Content-Type" content-type
                                                     "Cache-Control" "public, max-age=300"}))))

(defn app [config]
  (fn [request]
    (try
      (let [method (:request-method request)
            uri (:uri request)
            admin? (authenticated? request config)]
        (or (static-response uri)
            (case [method uri]
              [:get "/"] (html-response (views/dashboard admin?))
              [:get "/login"] (html-response (views/login false))
              [:post "/login"] (handle-login request config)
              [:get "/logout"] (logout-response request)
              [:get "/docs"] (html-response (views/docs))
              [:get "/health"] (json-response {:status "ok" :service "clojure-eventpulse"})
              [:get "/api/events"] (if admin? (handle-list-events request) (handle-mock-list-events request))
              [:get "/api/events/stats"] (json-response (if admin? (db/stats) (mock/stats)))
              [:get "/api/events/stream"] (update (if admin?
                                                    (sse/stream-response)
                                                    (sse/mock-stream-response))
                                                  :headers merge security-headers)
              [:post "/api/events"] (handle-post-event request config)
              (not-found))))
      (catch Exception e
        (.println System/err (str "Request failed: " (.getMessage e)))
        (json-response 500 {:error "Internal server error"})))))
