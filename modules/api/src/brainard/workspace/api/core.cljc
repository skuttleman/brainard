(ns brainard.workspace.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.maps :as maps]
    [brainard.api.utils.uuids :as uuids]
    [workspace-nodes :as-alias ws]))

(declare ->nodes)

(defn ^:private ->node [node]
  (-> node
      (maps/update-when ::ws/children ->nodes)
      (dissoc :brainard/ref ::ws/index)))

(defn ^:private ->nodes [nodes]
  (->> nodes
       (sort-by ::ws/index)
       (map ->node)))

(defn ^:private get-node [ws-api node-id]
  (storage/query (:store ws-api) {::storage/type ::fetch-by-id
                                  ::ws/id        node-id}))

(defn ^:private get-nodes [ws-api]
  (storage/query (:store ws-api)
                 {::storage/type ::select-by-parent-id}))

(defn create!
  "Creates a workspace node"
  [ws-api {::ws/keys [parent-id content]}]
  (let [node-id (uuids/random)]
    (storage/execute! (:store ws-api)
                      (cond-> {::storage/type ::create!
                               ::ws/id        node-id
                               ::ws/content   content}
                        parent-id (assoc ::ws/parent-id parent-id)))
    (->node (get-node ws-api node-id))))

(defn get-tree
  "Fetches the workspace tree"
  [ws-api]
  (->nodes (get-nodes ws-api)))

(defn delete!
  "Deletes a workspace node and any children recursively"
  [ws-api node-id]
  (storage/execute! (:store ws-api)
                    {::storage/type ::delete!
                     ::ws/id        node-id})
  nil)

(defn update!
  "Modifies or moves a workspace node"
  [ws-api node-id {::ws/keys [parent-id] :or {parent-id :same} :as params}]
  (storage/execute! (:store ws-api) (-> {::storage/type ::update!
                                         ::ws/id        node-id}
                                        (merge (select-keys params #{::ws/content
                                                                     ::ws/prev-sibling-id}))
                                        (cond-> parent-id (assoc ::ws/parent-id parent-id))))
  (->node (get-node ws-api node-id)))
