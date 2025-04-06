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

(defn ^:private handle-attachment-changes [version change {:keys [added removed]}]
  (let [prev-attachments (:attachments/previous version)
        next-attachments (-> {}
                             (into (filter (comp int? key)) change)
                             (update-vals #(update-vals % :to)))
        attachment-changes (into {}
                                 (keep (fn [id]
                                         (let [prev (get-in prev-attachments [id :attachments/name])
                                               next (get-in next-attachments [id :attachments/name])
                                               full (or next (get-in version [:attachments/state id :attachments/name]))]
                                           (when-let [update (cond
                                                               (contains? removed id)
                                                               {:removed full}

                                                               (contains? added id)
                                                               {:added full}

                                                               (and prev (not= prev full))
                                                               {:from prev
                                                                :to   full})]
                                             [id update]))))
                                 (into (set (keys (:attachments/state version)))
                                       (keys next-attachments)))]
    (-> version
        (update :attachments/state maps/deep-merge next-attachments)
        (assoc :attachments/previous next-attachments
               :attachments/changes attachment-changes))))

(defn ^:private reconstruct [prev changes]
  (let [{:notes/keys [attachments] :as changes} (-> changes
                                                    (update-in [:notes/attachments :added] set)
                                                    (update-in [:notes/attachments :removed] set)
                                                    (update :attachments/changes merge {}))]
    (reduce-kv (fn [version attr {:keys [added removed to] :as change}]
                 (cond-> version
                   (= :attachments/changes attr) (handle-attachment-changes change attachments)
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
