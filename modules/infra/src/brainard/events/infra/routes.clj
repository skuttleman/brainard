(ns brainard.events.infra.routes
  (:require
   [brainard :as-alias b]
   [brainard.api.utils.logger :as log]
   [brainard.infra.routes.interfaces :as iroutes]
   [brainard.api.events.interfaces :as ievents]
   [clojure.core.async :as async]
   [clojure.core.async.impl.protocols :as pasync]
   [manifold.stream :as s]
   [slag.utils.uuids :as uuids]
   [whet.core :as-alias w]))

(defn ^:private fmt-event [[event data]]
  (str "event: " (name event)
       (when data
         (str \newline "data: " (pr-str data)))
       \newline \newline))

(defn ^:private ->event-stream [ch]
  (let [ch-id (uuids/random)
        stream (s/stream 100)
        prom (promise)]
    (s/connect ch stream)
    (s/on-closed stream #(deliver prom nil))
    [ch-id stream prom]))

(defn ^:internal ^:no-doc handle-events [{::b/keys [events]} ->stream close-fn]
  (let [ch (async/chan 100 (map fmt-event))
        [id stream prom] (->stream ch)]
    (ievents/connect! events id {:ch ch :closed? prom})
    (async/go
      (async/>! ch [:connected])
      (log/infof "event stream connected: %s" id)
      (loop []
        (if (pasync/closed? ch)
          (do (ievents/disconnect! events id)
              (close-fn stream)
              (log/infof "event stream disconnected: %s" id))
          (do (async/<! (async/timeout 500))
              (recur)))))
    {:status  200
     ::w/raw? true
     :body    stream
     :headers {"Content-Type"  "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection"    "keep-alive"}}))

(defmethod iroutes/handler [:get :routes.api/events]
  [req]
  (handle-events req ->event-stream s/close!))
