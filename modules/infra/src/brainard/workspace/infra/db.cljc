(ns brainard.workspace.infra.db
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.infra.db.store :as ds]
    [brainard.workspace.api.core :as api.ws]
    [workspace-nodes :as-alias ws]))

(defn ^:private has-ancestor? [node ancestor-id]
  (or (= ancestor-id (::ws/id node))
      (boolean (some #(has-ancestor? % ancestor-id) (::ws/children node)))))

(defn delete-and-reindex [db id]
  (when-let [node (ds/query db (istorage/->input {::storage/type ::api.ws/fetch-by-id
                                                  ::ws/id        id}))]
    (let [nodes (ds/query db (istorage/->input {::storage/type ::api.ws/select-by-parent-id
                                                ::ws/parent-id (::ws/parent-id node)}))]
      (into [[:db/retractEntity [::ws/id id]]]
            (comp (remove (comp #{id} ::ws/id))
                  (map-indexed (fn [idx {::ws/keys [id]}]
                                 {::ws/id id ::ws/index idx})))
            (sort-by ::ws/index nodes)))))

(defn insert-sibling [db {::ws/keys [parent-id] :as node}]
  (let [parent (when parent-id
                 (ds/query db (istorage/->input {::storage/type ::api.ws/fetch-by-id
                                                 ::ws/id        parent-id})))
        siblings (ds/query db (istorage/->input {::storage/type ::api.ws/select-by-parent-id
                                                 ::ws/parent-id parent-id}))
        node (-> node
                 (select-keys #{::ws/id
                                ::ws/parent-id
                                ::ws/content
                                ::ws/children})
                 (assoc ::ws/index (count siblings)))]

    (if parent
      [{::ws/id       parent-id
        ::ws/children [node]}]
      [node])))

(defn update-node [db {::ws/keys [content id parent-id prev-sibling-id]}]
  (let [same-parent? (= :same parent-id)]
    (when-let [node (ds/query db (istorage/->input {::storage/type ::api.ws/fetch-by-id
                                                    ::ws/id        id}))]
      (when (or (nil? parent-id) same-parent? (not (has-ancestor? node parent-id)))
        (let [curr-parent-id (::ws/parent-id node)
              root-nodes (when (or (nil? curr-parent-id) (nil? parent-id))
                           (ds/query db (istorage/->input {::storage/type ::api.ws/select-by-parent-id})))
              curr-parent (if (nil? curr-parent-id)
                            {::ws/children root-nodes}
                            (ds/query db (istorage/->input {::storage/type ::api.ws/fetch-by-id
                                                            ::ws/id        curr-parent-id})))
              next-parent-id (if same-parent?
                               curr-parent-id
                               parent-id)
              next-parent (cond
                            same-parent?
                            curr-parent

                            (nil? next-parent-id)
                            {::ws/children root-nodes}

                            :else
                            (ds/query db (istorage/->input {::storage/type ::api.ws/fetch-by-id
                                                            ::ws/id        next-parent-id})))
              same-parent? (= curr-parent-id next-parent-id)
              sibling (when prev-sibling-id
                        (->> (::ws/children next-parent)
                             (filter (comp #{prev-sibling-id} ::ws/id))
                             first))
              next-node (cond-> {::ws/id id}
                          content
                          (assoc ::ws/content content)

                          (some? next-parent-id)
                          (assoc ::ws/parent-id next-parent-id))
              reorder-old (when-not same-parent?
                            (->> (::ws/children curr-parent)
                                 (remove (comp #{id} ::ws/id))
                                 (sort-by ::ws/index)
                                 (map-indexed (fn [idx {::ws/keys [id]}]
                                                {::ws/id id ::ws/index idx}))))
              reorder-new (cond
                            sibling
                            (->> (::ws/children next-parent)
                                 (remove (comp #{id} ::ws/id))
                                 (sort-by ::ws/index)
                                 (map #(select-keys % #{::ws/id}))
                                 (mapcat (fn [node]
                                           (cond-> [node]
                                             (= prev-sibling-id (::ws/id node))
                                             (conj next-node))))
                                 (map-indexed (fn [idx node]
                                                (assoc node ::ws/index idx))))

                            same-parent?
                            [next-node]

                            :else
                            (->> (::ws/children next-parent)
                                 (sort-by ::ws/index)
                                 (map #(select-keys % #{::ws/id}))
                                 (cons next-node)
                                 (map-indexed (fn [idx node]
                                                (assoc node ::ws/index idx)))))]
          (cond-> (into [] (concat reorder-old reorder-new))
            (and curr-parent-id (not= curr-parent-id next-parent-id))
            (-> (conj [:db/retract [::ws/id id] ::ws/parent-id curr-parent-id]
                      [:db/retract [::ws/id curr-parent-id] ::ws/children (:brainard/ref node)]))

            (and next-parent-id (not same-parent?))
            (conj [:db/add [::ws/id next-parent-id] ::ws/children (:brainard/ref node)])))))))

(defmethod istorage/->input ::api.ws/fetch-by-id
  [{::ws/keys [id]}]
  {:query '[:find (pull ?e [*])
            :in $ ?node-id
            :where [?e ::ws/id ?node-id]]
   :args  [id]
   :only? true
   :ref?  true
   :xform (map first)})

(defmethod istorage/->input ::api.ws/select-by-parent-id
  [{::ws/keys [parent-id]}]
  {:query (cond-> '[:find (pull ?e [*])
                    :where
                    [?e ::ws/id _]]
            (some? parent-id)
            (conj ['?e ::ws/parent-id parent-id])

            (nil? parent-id)
            (conj '[(missing? $ ?e ::ws/parent-id)]))
   :xform (map first)})

(defmethod istorage/->input ::api.ws/create!
  [node]
  [#?(:clj  `[insert-sibling ~node]
      :cljs [:db.fn/call insert-sibling node])])

(defmethod istorage/->input ::api.ws/update!
  [node]
  [#?(:clj  `[update-node ~node]
      :cljs [:db.fn/call update-node node])])

(defmethod istorage/->input ::api.ws/delete!
  [{::ws/keys [id]}]
  [#?(:clj  `[delete-and-reindex ~id]
      :cljs [:db.fn/call delete-and-reindex id])])
