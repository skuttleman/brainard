(ns brainard.notifications.infra.manager
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.utils.edn :as edn]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]
    [haslett.client :as ws]))

(defn ^:private ->conn []
  (ws/connect "/api/ws"
              {:in (async/chan 10 (map edn/read-string))}))

(defn loop! [store]
  (async/go-loop [stream (async/<! (->conn))]
    (let [[_ {:keys [data]} :as val] (async/<! (:in stream))]
      (when val
        (defacto/emit! store [::res/swapped [::specs/notes#buzz] data]))
      (if (and val (ws/connected? stream))
        (recur stream)
        (do (println "reconnecting...")
            (recur (async/<! (->conn))))))))
