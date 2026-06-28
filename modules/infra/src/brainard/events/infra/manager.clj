(ns brainard.events.infra.manager
  (:require
   [brainard.api.events.interfaces :as ievents]
   [cljc.java-time.instant :as inst]
   [clojure.core.async :as async]))

(defn ^:private filter-items [v ttl]
  (filterv (fn [m]
             (not (inst/is-before (:inst m) (inst/minus-millis (inst/now) ttl))))
           v))

(defn ^:private add! [v-atm ttl item]
  (swap! v-atm (comp #(conj % {:inst (inst/now) :data item}) filter-items) ttl))

(defn ^:private items [v-atm ttl]
  (map :data (swap! v-atm filter-items ttl)))

(deftype EventsManager [subs v-atm ttl]
  ievents/IConnect
  (connect! [_ ch-id conn]
    (dosync
     (alter subs assoc ch-id conn))
    (run! (partial async/put! (:ch conn)) (items v-atm ttl)))
  (close! [this]
    (run! (partial ievents/disconnect! this) (keys @subs)))
  (disconnect! [_ ch-id]
    (dosync
     (when-let [{:keys [ch closed? close!]} (get @subs ch-id)]
       (some-> ch async/close!)
       (when close! (close!))
       (some-> closed? (deref 500 nil)))
     (alter subs dissoc ch-id)))

  ievents/ISend
  (broadcast! [_ type data]
    (let [item [:message [type data]]]
      (add! v-atm ttl item)
      (doseq [{:keys [ch]} (vals @subs)]
        (async/put! ch item)))))

(defn create
  "Creates an EventsManager"
  [ttl]
  (->EventsManager (ref {}) (atom []) ttl))
