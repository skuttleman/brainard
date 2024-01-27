(ns brainard.workspace.api.core
  (:require
    [brainard.api.utils.logger :as log]
    [brainard.storage.core :as storage]
    [brainard.api.utils.uuids :as uuids]
    [brainard.storage.interfaces :as istorage]))

(defn ^:private collect-ids [tree]
  (into #{(:workspace-nodes/id tree)} (mapcat collect-ids) (:workspace-nodes/nodes tree)))

(defn create!
  "Creates a workspace node"
  [workspace-api {:workspace-nodes/keys [parent-id data]}]
  (let [parent (when parent-id
                 (storage/query (:store workspace-api)
                                {::storage/type      ::get-by-id
                                 :workspace-nodes/id parent-id}))
        node (cond-> {:workspace-nodes/id   (uuids/random)
                      :workspace-nodes/data data}
               parent (assoc :workspace-nodes/parent-id parent-id))
        data (cond-> node
               parent (->> vector (hash-map :workspace-nodes/id parent-id :workspace-nodes/nodes)))]
    (storage/execute! (:store workspace-api) (assoc data ::storage/type ::save!))
    node))

(defn delete!
  "Removes a workspace node"
  [workspace-api node-id]
  (storage/execute! (:store workspace-api) {::storage/type      ::delete!
                                            :workspace-nodes/id node-id}))

(defn move!
  "Nests a node under a parent"
  ([workspace-api parent-id root-node-id]
   (move! workspace-api nil parent-id root-node-id))
  ([workspace-api old-parent-id new-parent-id node-id]
   (when-not (= old-parent-id new-parent-id)
     (let [child (storage/query (:store workspace-api) {::storage/type      ::get-by-id
                                                        :workspace-nodes/id node-id})
           ref (storage/node->ref child)]
       (when-not (= old-parent-id (:workspace-nodes/parent-id child))
         (throw (ex-info "node not found as child on parent" {:child-id node-id :parent-id old-parent-id})))
       (when (contains? (collect-ids child) new-parent-id)
         (throw (ex-info "node cannot be made a child of its ancestor" {:child-id node-id :parent-id new-parent-id})))
       (storage/execute! (:store workspace-api)
                         {::storage/type             ::detach!
                          :workspace-nodes/id        node-id
                          :workspace-nodes/parent-id old-parent-id
                          :workspace-nodes/ref       ref}
                         {::storage/type                 ::attach!
                          :workspace-nodes/id            node-id
                          :workspace-nodes/old-parent-id old-parent-id
                          :workspace-nodes/new-parent-id new-parent-id
                          :workspace-nodes/ref           ref})))))

(defn detach!
  "Moves a subtree to the root of the workspace."
  [workspace-api old-parent-id node-id]
  (storage/execute! (:store workspace-api) {::storage/type             ::detach!
                                            :workspace-nodes/id        node-id
                                            :workspace-nodes/parent-id old-parent-id}))

(defn get-workspace
  "Retrieves the workspace tree"
  [workspace-api]
  (storage/query (:store workspace-api) {::storage/type ::get-workspace}))
