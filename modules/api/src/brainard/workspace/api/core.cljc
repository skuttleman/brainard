(ns brainard.workspace.api.core
  (:require
    [brainard.api.utils.uuids :as uuids]
    [brainard.workspace.api.interfaces :as iwork]))

(defn create!
  "Creates a workspace node"
  [workspace-api {:workspace-nodes/keys [parent-id data]}]
  (let [parent (some->> parent-id (iwork/get-by-id (:store workspace-api)))
        node (cond-> {:workspace-nodes/id   (uuids/random)
                      :workspace-nodes/data data}
               parent (assoc :workspace-nodes/parent-id parent-id))]
    (if parent
      (iwork/save! (:store workspace-api) {:workspace-nodes/id    parent-id
                                           :workspace-nodes/nodes [node]})
      (iwork/save! (:store workspace-api) node))
    node))

(defn remove!
  "Removes a workspace node"
  [workspace-api node-id]
  (iwork/delete! (:store workspace-api) node-id))

(defn move!
  "Nests a node under a parent"
  ([workspace-api parent-id root-node-id]
   (move! workspace-api nil parent-id root-node-id))
  ([workspace-api old-parent-id new-parent-id node-id]
   (let [child (iwork/get-by-id (:store workspace-api) node-id)]
     (when-not (= old-parent-id (:workspace-nodes/parent-id child))
       (throw (ex-info "child not found on parent" {:child-id node-id :parent-id old-parent-id})))
     (iwork/move-node! (:store workspace-api)
                       old-parent-id
                       new-parent-id
                       node-id))))

(defn detach!
  "Moves a subtree to the root of the workspace."
  [workspace-api node-id]
  (iwork/detach-node! (:store workspace-api) node-id))

(defn get-workspace
  "Retrieves the workspace tree"
  [workspace-api]
  (iwork/get-all (:store workspace-api)))
