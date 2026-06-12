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
    (run! (partial ievents/disconnect! this) (keys @subs))
   #_#_(doseq [{:keys [ch close-wait]} (vals @subs)]
         (async/close! ch))
           (dosync
            (ref-set subs {})))
  (disconnect! [_ ch-id]
    (dosync
     (when-let [{:keys [ch close-wait]} (get @subs ch-id)]
       (some-> ch async/close!)
       (some-> close-wait deref))
     (alter subs dissoc ch-id)))

  ievents/ISend
  (broadcast! [_ type data]
    (doseq [{:keys [ch]} (vals @subs)]
      (async/put! ch [type data]))))
