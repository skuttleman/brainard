(ns brainard.events.infra.manager
  (:require
    [clojure.core.async :as async]
    [brainard.api.events.interfaces :as ievents]))

(deftype EventsManager [subs]
  ievents/IConnect
  (connect! [_ ch-id ch]
    (dosync
      (alter subs assoc ch-id ch)))
  (close! [_]
    (doseq [ch (vals @subs)]
      (async/close! ch))
    (dosync
      (ref-set subs {})))
  (disconnect! [_ ch-id]
    (dosync
      (some-> (get @subs ch-id) async/close!)
      (alter subs dissoc ch-id)))

  ievents/ISend
  (broadcast! [_ type data]
    (doseq [ch (vals @subs)]
      (async/put! ch [type data]))))
