(ns eventpulse.sse
  (:require [cheshire.core :as json]
            [ring.core.protocols :as protocols])
  (:import [java.io IOException OutputStream]
           [java.util UUID]
           [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defonce subscribers (atom {}))

(defn- sse-bytes [event]
  (.getBytes (str "data: " (json/generate-string event) "\n\n") "UTF-8"))

(defn- ping-bytes []
  (.getBytes ": ping\n\n" "UTF-8"))

(defn broadcast! [event]
  (doseq [[id queue] @subscribers]
    (when-not (.offer queue event)
      (.poll queue)
      (when-not (.offer queue event)
        (swap! subscribers dissoc id)))))

(defn- write-stream! [id queue ^OutputStream out]
  (try
    (loop []
      (let [event (.poll queue 25 TimeUnit/SECONDS)]
        (if event
          (.write out (sse-bytes event))
          (.write out (ping-bytes)))
        (.flush out)
        (recur)))
    (catch IOException _ nil)
    (catch Exception e
      (.println System/err (str "SSE stream error: " (.getMessage e))))
    (finally
      (swap! subscribers dissoc id))))

(defn stream-response []
  (let [id (str (UUID/randomUUID))
        queue (LinkedBlockingQueue. 100)]
    (swap! subscribers assoc id queue)
    (.offer queue {:event "connected"})
    {:status 200
     :headers {"Content-Type" "text/event-stream; charset=utf-8"
               "Cache-Control" "no-cache, no-transform"
               "Connection" "keep-alive"
               "X-Accel-Buffering" "no"}
     :body (reify protocols/StreamableResponseBody
             (write-body-to-stream [_ _ output-stream]
               (write-stream! id queue output-stream)))}))

(defn mock-stream-response []
  {:status 200
   :headers {"Content-Type" "text/event-stream; charset=utf-8"
             "Cache-Control" "no-cache, no-transform"
             "Connection" "keep-alive"
             "X-Accel-Buffering" "no"}
   :body (reify protocols/StreamableResponseBody
           (write-body-to-stream [_ _ output-stream]
             (try
               (.write output-stream (sse-bytes {:event "connected" :demo true}))
               (.flush output-stream)
               (loop []
                 (Thread/sleep 30000)
                 (.write output-stream (sse-bytes {:event "demo-refresh" :demo true}))
                 (.flush output-stream)
                 (recur))
               (catch IOException _ nil)
               (catch Exception e
                 (.println System/err (str "Mock SSE stream error: " (.getMessage e)))))))})
