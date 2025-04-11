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

(defn ^:private handle-attachment-changes [{{:keys [added removed]} :notes/attachments :as change} version]
  (let [prev-attachments (:attachments/previous version)
        next-attachments (-> {}
                             (into (filter (comp int? key)) (:attachments/changes change))
                             (update-vals (partial into
                                                   {}
                                                   (keep (fn [[k {:keys [to]}]]
                                                           (when to
                                                             [k to]))))))
        attachment-changes (into {}
                                 (keep (fn [id]
                                         (let [prev (or (get-in prev-attachments [id :attachments/name])
                                                        (get-in change [:attachments/changes id :attachments/name :from]))
                                               next (or (get-in next-attachments [id :attachments/name])
                                                        (get-in version [:attachments/state id :attachments/name]))]
                                           (when-let [update (cond
                                                               (contains? removed id)
                                                               {:removed (or next prev)}

                                                               (contains? added id)
                                                               {:added next}

                                                               (and prev (not= prev next))
                                                               {:from prev
                                                                :to   next})]
                                             [id update]))))
                                 (into #{}
                                       (concat (keys (:attachments/state version))
                                               (keys (:attachments/changes version))
                                               (keys next-attachments)
                                               added
                                               removed)))]
    (-> version
        (update :attachments/state maps/deep-merge next-attachments)
        (assoc :attachments/previous (:attachments/state version)
               :attachments/changes attachment-changes))))

(defn ^:private reconstruct [prev changes]
  (let [changes (-> changes
                    (update-in [:notes/attachments :added] set)
                    (update-in [:notes/attachments :removed] set)
                    (update :attachments/changes merge {}))]
    (->> changes
         (reduce-kv (fn [version attr {:keys [added removed to]}]
                      (cond-> version
                        (some? to) (assoc attr to)
                        added (update attr (fnil into #{}) added)
                        removed (update attr (partial apply disj) removed)))
                    (dissoc prev :attachments/changes))
         (handle-attachment-changes changes))))

(defn ^:private history-reducer [versions {:notes/keys [changes history-id saved-at]}]
  (let [[_ prev] (peek versions)
        prev (assoc prev
                    :notes/history-id history-id
                    :notes/saved-at saved-at)]
    (conj versions [history-id (reconstruct prev changes)])))

(defmethod defacto/query-responder :notes.history/?:reconstruction
  [db [_ spec]]
  (let [res (defacto/query-responder db [::res/?:resource spec])]
    (when (res/success? res)
      (->> res
           res/payload
           (reduce history-reducer [])
           (into {})))))
