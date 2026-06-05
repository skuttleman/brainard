(ns brainard.notifications.infra.manager
  (:require
    [brainard.api.utils.logger :as log]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.utils.edn :as edn]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]
    [haslett.client :as ws]))

(defn ^:private ->conn []
  (log/info "establishing websocket connection...")
  (ws/connect "/api/ws" {:in (async/chan 10 (map edn/read-string))}))

(defn ^:private backoff [wait-ms]
  (-> wait-ms
      (* 7)
      (max 1)
      (min 3000)))

(defn loop! [store]
  (async/go-loop [stream (async/<! (->conn))
                  new? true
                  wait 0]
    (when (and new? (ws/connected? stream))
      (log/info "websocket connection established"))
    (if (ws/connected? stream)
      (let [[_ {:keys [data]} :as val] (async/<! (:in stream))]
        (when val
          (defacto/emit! store [::res/swapped [::specs/notes#buzz] data]))
        (recur stream false 0))
      (do (async/<! (async/timeout wait))
          (recur (async/<! (->conn)) true (backoff wait))))))
