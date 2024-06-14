(ns brainard.infra.store.queries
  (:require
    [defacto.core :as defacto]
    [defacto.resources.core :as res]))

(defmethod defacto/query-responder :modals/?:modals
  [db _]
  (->> (:modals/modals db)
       (sort-by key)
       (map (fn [[modal-id modal]]
              (assoc modal :id modal-id)))))

(defmethod defacto/query-responder :toasts/?:toasts
  [db _]
  (->> (:toasts/toasts db)
       (sort-by key)
       (take 3)
       (map (fn [[toast-id toast]]
              (assoc toast :id toast-id)))))

(defmethod defacto/query-responder :toasts/?:toast
  [db [_ toast-id]]
  (some-> (get-in db [:toasts/toasts toast-id])
          (assoc :id toast-id)))

(defn ^:private reconstruct [prev changes]
  (reduce-kv (fn [version attr {:keys [added removed to]}]
               (cond-> version
                 (some? to) (assoc attr to)
                 added (update attr (fnil into #{}) added)
                 removed (update attr (partial apply disj) removed)))
             prev
             changes))

(defmethod defacto/query-responder :notes.history/?:reconstruction
  [db [_ spec]]
  (let [res (defacto/query-responder db [::res/?:resource spec])]
    (when (res/success? res)
      (->> res
           res/payload
           (reduce (fn [versions {:notes/keys [changes history-id saved-at]}]
                     (let [[_ prev] (peek versions)
                           prev (assoc prev
                                       :notes/history-id history-id
                                       :notes/saved-at saved-at)]
                       (conj versions [history-id (reconstruct prev changes)])))
                   [])
           (into {})))))