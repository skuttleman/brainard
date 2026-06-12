(ns brainard.infra.store.queries
  (:require
   [defacto.core :as defacto]
   [defacto.resources.core :as res]
   [slag.utils.maps :as maps]))

(defmethod defacto/query-responder :modals/?:modals
  [db [_ modifier]]
  (->> (:modals/modals db)
       (sort-by key)
       (map (fn [[modal-id modal]]
              (assoc modal :id modal-id)))
       ((or modifier identity))))

(defmethod defacto/query-responder :toasts/?:toasts
  [db _]
  (->> (:toasts/toasts db)
       (sort-by key)
       (take 4)
       (map (fn [[toast-id toast]]
              (assoc toast :id toast-id)))))

(defmethod defacto/query-responder :toasts/?:toast
  [db [_ toast-id]]
  (some-> (get-in db [:toasts/toasts toast-id])
          (assoc :id toast-id)))

(defn ^:private handle-changes [change type change-fn version]
  (let [type (name type)
        note-k (keyword "notes" type)
        prev-k (keyword type "previous")
        changes-k (keyword type "changes")
        state-k (keyword type "state")
        {:keys [added removed]} (note-k change)
        prev-items (state-k version)
        next-items (-> {}
                       (into (filter (comp int? key)) (changes-k change))
                       (update-vals (partial into
                                             {}
                                             (keep (fn [[k {:keys [to]}]]
                                                     (when (some? to)
                                                       [k to]))))))
        item-changes (into {}
                           (keep (partial change-fn prev-items next-items))
                           (into #{}
                                 (concat (keys (state-k version))
                                         (keys (changes-k version))
                                         (keys next-items)
                                         added
                                         removed)))]
    (-> version
        (update state-k maps/deep-merge next-items)
        (assoc prev-k prev-items changes-k item-changes))))

(defn ^:private extract-diffs [prev-items next-items change version id ns ks]
  (let [change-k (keyword (name ns) "changes")
        state-k (keyword (name ns) "state")]
    (into {}
          (mapcat (fn [k]
                    (let [el-k (keyword (name ns) (name k))
                          prev-k (keyword (str "prev-" (name k)))
                          next-k (keyword (str "next-" (name k)))
                          prev (get-in prev-items [id el-k])
                          prev (if (some? prev)
                                 prev
                                 (get-in change [change-k id el-k :from]))
                          next (get-in next-items [id el-k])
                          next (if (some? next)
                                 next
                                 (get-in version [state-k id el-k]))]
                      [[prev-k prev] [next-k next]])))
          ks)))

(defn ^:private handle-attachment-changes [{{:keys [added removed]} :notes/attachments :as change} version]
  (->> version
       (handle-changes change
                       :attachments
                       (fn [prev-attachments next-attachments id]
                         (let [{:keys [prev-name next-name]} (extract-diffs prev-attachments
                                                                            next-attachments
                                                                            change
                                                                            version
                                                                            id
                                                                            :attachments
                                                                            [:name])]
                           (when-let [update (cond
                                               (contains? removed id)
                                               {:removed (or next-name prev-name)}

                                               (contains? added id)
                                               {:added next-name}

                                               (and prev-name (not= prev-name next-name))
                                               {:from prev-name
                                                :to   next-name})]
                             [id update]))))))

(defn ^:private handle-todo-changes [{{:keys [added removed]} :notes/todos :as change} version]
  (->> version
       (handle-changes change
                       :todos
                       (fn [prev-todos next-todos id]
                         (let [{:keys [prev-completed? next-completed? prev-text next-text]}
                               (extract-diffs prev-todos
                                              next-todos
                                              change
                                              version
                                              id
                                              :todos
                                              [:text :completed?])]
                           (when-let [update (cond
                                               (contains? removed id)
                                               {:removed (or next-text prev-text)}

                                               (contains? added id)
                                               {:added next-text
                                                :done? (boolean next-completed?)}

                                               (and prev-text (not= prev-text next-text))
                                               (cond-> {:from prev-text
                                                        :to   next-text}
                                                 (and (or prev-completed? next-completed?)
                                                      (not= prev-completed? next-completed?))
                                                 (assoc :done? next-completed?))

                                               (and (some? next-completed?)
                                                    (not= prev-completed? next-completed?))
                                               {:todo  (or next-text prev-text)
                                                :done? next-completed?})]
                             [id update]))))))

(defn ^:private reconstruct [prev changes]
  (let [changes (-> changes
                    (update-in [:notes/attachments :added] set)
                    (update-in [:notes/attachments :removed] set)
                    (update-in [:notes/todos :added] set)
                    (update-in [:notes/todos :removed] set)
                    (update :attachments/changes merge {})
                    (update :todos/changes merge {}))]
    (->> changes
         (reduce-kv (fn [version attr {:keys [added removed to]}]
                      (cond-> version
                        (some? to) (assoc attr to)
                        added (update attr (fnil into #{}) added)
                        removed (update attr (partial apply disj) removed)))
                    (dissoc prev :attachments/changes :todos/changes))
         (handle-attachment-changes changes)
         (handle-todo-changes changes))))

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
