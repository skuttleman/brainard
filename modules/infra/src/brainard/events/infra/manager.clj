(ns brainard.events.infra.manager
  (:require
   [clojure.core.async :as async]
   [brainard.api.events.interfaces :as ievents]))

(deftype EventsManager [subs]
  ievents/IConnect
  (connect! [_ ch-id conn]
    (dosync
     (alter subs assoc ch-id conn)))
  (close! [this]
    (run! (partial ievents/disconnect! this) (keys @subs)))
  (disconnect! [_ ch-id]
    (dosync
     (when-let [{:keys [ch closed?]} (get @subs ch-id)]
       (some-> ch async/close!)
       (some-> closed? (deref 500 nil)))
     (alter subs dissoc ch-id)))

  ievents/ISend
  (broadcast! [_ type data]
    (doseq [{:keys [ch]} (vals @subs)]
      (async/put! ch [type data]))))
