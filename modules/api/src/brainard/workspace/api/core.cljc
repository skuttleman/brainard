(ns brainard.workspace.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.uuids :as uuids]))

(def ^:private ^:const insertion-index
  #?(:cljs js/Number.MAX_SAFE_INTEGER :default Long/MAX_VALUE))

(defn ^:private collect-ids [tree]
  (into #{(:workspace-nodes/id tree)} (mapcat collect-ids) (:workspace-nodes/nodes tree)))

(defn ^:private reorder [workspace-api parent-id]
  (->> {::storage/type             ::get-by-parent-id
        :workspace-nodes/parent-id parent-id}
       (storage/query (:store workspace-api))
       (sort-by (juxt :workspace-nodes/index :workspace-nodes/id))
       (map-indexed (fn [idx node]
                      (assoc node ::storage/type ::save! :workspace-nodes/index idx)))))

(defn create!
  "Creates a workspace node"
  [workspace-api {:workspace-nodes/keys [parent-id data]}]
  (let [parent (when parent-id
                 (storage/query (:store workspace-api)
                                {::storage/type      ::get-by-id
                                 :workspace-nodes/id parent-id}))
        node (cond-> {:workspace-nodes/id    (uuids/random)
                      :workspace-nodes/index insertion-index
                      :workspace-nodes/data  data}
               parent (assoc :workspace-nodes/parent-id parent-id))
        data (cond-> node
               parent (->> vector (hash-map :workspace-nodes/id parent-id :workspace-nodes/nodes)))]
    (storage/execute! (:store workspace-api)
                      (assoc data ::storage/type ::save!))
    (apply storage/execute! (:store workspace-api) (reorder workspace-api parent-id))
    node))

(defn delete!
  "Removes a workspace node"
  [workspace-api node-id]
  (storage/execute! (:store workspace-api) {::storage/type      ::delete-by-id!
                                            :workspace-nodes/id node-id}))

(defn up-order!
  ""
  [workspace-api node-id]
  (let [{:workspace-nodes/keys [index parent-id]} (storage/query (:store workspace-api)
                                                                 {::storage/type      ::get-by-id
                                                                  :workspace-nodes/id node-id})]
    (storage/execute! (:store workspace-api)
                      {::storage/type         ::save!
                       :workspace-nodes/id    node-id
                       :workspace-nodes/index (- index 1.5)})
    (apply storage/execute!
           (:store workspace-api)
           (reorder workspace-api parent-id))))

(defn down-order!
  ""
  [workspace-api node-id]
  (when-let [{:workspace-nodes/keys [index parent-id]} (storage/query (:store workspace-api)
                                                                      {::storage/type      ::get-by-id
                                                                       :workspace-nodes/id node-id})]
    (storage/execute! (:store workspace-api)
                      {::storage/type         ::save!
                       :workspace-nodes/id    node-id
                       :workspace-nodes/index (+ index 1.5)})
    (apply storage/execute!
           (:store workspace-api)
           (reorder workspace-api parent-id))))

(defn nest!
  ""
  [workspace-api node-id]
  (let [node (storage/query (:store workspace-api)
                       {::storage/type      ::get-by-id
                        :workspace-nodes/id node-id})
        parent-id (:workspace-nodes/parent-id node)
        prev (->> (storage/query (:store workspace-api)
                                 {::storage/type             ::get-by-parent-id
                                  :workspace-nodes/parent-id parent-id})
                  (sort-by :workspace-nodes/index >)
                  (drop-while (comp (complement #{node-id}) :workspace-nodes/id))
                  rest
                  first)
        ref (storage/node->ref node)]
    (when-not prev
      (throw (ex-info "cannot nest node" {:node-id node-id})))
    (storage/execute! (:store workspace-api)
                      {::storage/type             ::move-root!
                       :workspace-nodes/id node-id
                       :workspace-nodes/old-parent-id parent-id
                       :workspace-nodes/new-parent-id (:workspace-nodes/id prev)
                       :brainard/ref ref}
                      {::storage/type             ::save!
                       :workspace-nodes/id        node-id
                       :workspace-nodes/index     insertion-index})
    (apply storage/execute!
           (:store workspace-api)
           (concat (reorder workspace-api (:workspace-nodes/id prev))
                   (reorder workspace-api parent-id)))))

(defn unnest!
  ""
  [workspace-api node-id]
  (let [{:workspace-nodes/keys [parent-id] :as node} (storage/query (:store workspace-api)
                                                                    {::storage/type      ::get-by-id
                                                                     :workspace-nodes/id node-id})
        parent (when parent-id
                 (storage/query (:store workspace-api)
                                {::storage/type      ::get-by-id
                                 :workspace-nodes/id parent-id}))
        ref (storage/node->ref node)]
    (when-not parent
      (throw (ex-info "node already a top level" {:node-id node-id})))
    (storage/execute! (:store workspace-api)
                      (if-let [new-parent-id (:workspace-nodes/parent-id parent)]
                        {::storage/type                 ::move-root!
                         :workspace-nodes/id            node-id
                         :workspace-nodes/old-parent-id parent-id
                         :workspace-nodes/new-parent-id new-parent-id
                         :brainard/ref                  ref}
                        {::storage/type             ::de-root!
                         :workspace-nodes/id        node-id
                         :workspace-nodes/parent-id parent-id
                         :brainard/ref              ref}))
    (apply storage/execute!
           (:store workspace-api)
           (reorder workspace-api (:workspace-nodes/parent-id parent)))
    (when parent-id
      (apply storage/execute!
             (:store workspace-api)
             (reorder workspace-api parent-id)))))

(defn get-workspace
  "Retrieves the workspace tree"
  [workspace-api]
  (->> (storage/query (:store workspace-api) {::storage/type ::get-workspace})
       (sort-by :workspace-nodes/index)))
