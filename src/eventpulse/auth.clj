(ns eventpulse.auth
  (:require [clojure.string :as str])
  (:import [java.security MessageDigest SecureRandom]
           [java.util Base64]
           [javax.crypto SecretKeyFactory]
           [javax.crypto.spec PBEKeySpec]))

(def password-iterations 210000)
(def password-key-bits 256)
(def session-ttl-ms (* 8 60 60 1000))
(def max-sessions 16)
(defonce sessions (atom {}))
(defonce random (SecureRandom.))

(defn now-ms []
  (System/currentTimeMillis))

(defn constant= [a b]
  (let [left (.getBytes (or a "") "UTF-8")
        right (.getBytes (or b "") "UTF-8")]
    (MessageDigest/isEqual left right)))

(defn random-bytes [n]
  (let [bytes (byte-array n)]
    (.nextBytes random bytes)
    bytes))

(defn b64-encode [bytes]
  (.encodeToString (Base64/getUrlEncoder) bytes))

(defn b64-decode [value]
  (.decode (Base64/getUrlDecoder) value))

(defn random-token []
  (b64-encode (random-bytes 32)))

(defn pbkdf2 [password salt iterations key-bits]
  (let [factory (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA256")
        spec (PBEKeySpec. (char-array (or password "")) salt iterations key-bits)]
    (try
      (.getEncoded (.generateSecret factory spec))
      (finally
        (.clearPassword spec)))))

(defn hash-password [password]
  (let [salt (random-bytes 16)
        hash (pbkdf2 password salt password-iterations password-key-bits)]
    (str "pbkdf2_sha256$" password-iterations "$" (b64-encode salt) "$" (b64-encode hash))))

(defn verify-password-hash? [password encoded]
  (try
    (let [[scheme iterations salt hash] (str/split (or encoded "") #"\$")]
      (and (= scheme "pbkdf2_sha256")
           (seq iterations)
           (seq salt)
           (seq hash)
           (let [iterations (Integer/parseInt iterations)
                 expected (b64-decode hash)
                 actual (pbkdf2 password (b64-decode salt) iterations (* 8 (alength expected)))]
             (MessageDigest/isEqual expected actual))))
    (catch Exception _ false)))

(defn cleanup-sessions! []
  (let [cutoff (now-ms)]
    (swap! sessions
           (fn [current]
             (->> current
                  (filter (fn [[_ session]] (> (:expires-at session) cutoff)))
                  (sort-by (comp :created-at second) >)
                  (take max-sessions)
                  (into {}))))))

(defn create-session! [username]
  (cleanup-sessions!)
  (let [token (random-token)
        now (now-ms)]
    (swap! sessions assoc token {:username username
                                 :created-at now
                                 :expires-at (+ now session-ttl-ms)})
    token))

(defn valid-session? [token username]
  (cleanup-sessions!)
  (boolean
   (when-let [session (get @sessions token)]
     (and (constant= username (:username session))
          (> (:expires-at session) (now-ms))))))

(defn destroy-session! [token]
  (swap! sessions dissoc token))

(defn -main [& _args]
  (let [password (str/trim (slurp *in*))]
    (when (str/blank? password)
      (throw (ex-info "Password must be provided on stdin" {})))
    (println (hash-password password))))
