(ns brainard.infra.store.queries
  (:require
    [brainard.api.utils.maps :as maps]
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

(defn ^:private handle-attachment-changes [version {:keys [added removed] :as change}]
  (let [prev-attachments (:attachments/state version)
        next-attachments (-> {}
                             (into (filter (comp int? key)) change)
                             (update-vals #(update-vals % :to)))
        attachment-changes (into {}
                                 (keep (fn [id]
                                         (when (not= (get prev-attachments id)
                                                     (get next-attachments id))
                                           [id (cond
                                                 (or (nil? (get prev-attachments id))
                                                     ((set added) id))
                                                 {:added (get-in next-attachments [id :attachments/name])}

                                                 (or (nil? (get next-attachments id))
                                                     ((set removed) id))
                                                 {:removed (get-in prev-attachments [id :attachments/name])}

                                                 :else
                                                 {:from (get-in prev-attachments [id :attachments/name])
                                                  :to   (get-in next-attachments [id :attachments/name])})])))
                                 (into (set (keys prev-attachments))
                                       (keys next-attachments)))]
    (-> version
        (assoc :attachments/state next-attachments
               :attachments/changes attachment-changes))))

(defn ^:private reconstruct [prev changes]
  (let [changes (-> changes
                    (update-in [:notes/attachments :added] set)
                    (update-in [:notes/attachments :removed] set)
                    (update :attachments/changes merge {}))]
    (reduce-kv (fn [version attr {:keys [added removed to] :as change}]
                 (cond-> version
                   (= :attachments/changes attr) (handle-attachment-changes change)
                   (some? to) (assoc attr to)
                   added (update attr (fnil into #{}) added)
                   removed (update attr (partial apply disj) removed)))
               prev
               changes)))

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
