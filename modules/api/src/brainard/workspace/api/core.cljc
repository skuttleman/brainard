(ns brainard.workspace.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.maps :as maps]
    [brainard.api.utils.uuids :as uuids]
    [workspace-nodes :as-alias ws]))

(declare ->nodes)

(def ^:private ^:const insertion-index
  #?(:cljs js/Number.MAX_SAFE_INTEGER :default Long/MAX_VALUE))

(defn ^:private reorder [ws-api parent-id]
  (->> (storage/query (:store ws-api) {::storage/type ::select-by-parent-id
                                       ::ws/parent-id parent-id})
       (sort-by (juxt ::ws/index ::ws/id))
       (map-indexed (fn [idx {::ws/keys [id]}]
                      {::storage/type ::save!
                       ::ws/id        id
                       ::ws/index     idx}))))

(defn ^:private ->node [node]
  (-> node
      (maps/update-when ::ws/children ->nodes)
      (dissoc ::ws/index)))

(defn ^:private ->nodes [nodes]
  (->> nodes
       (sort-by ::ws/index)
       (map ->node)))

(defn ^:private has-ancestor? [node ancestor-id]
  (or (= ancestor-id (::ws/id node))
      (boolean (some #(has-ancestor? % ancestor-id) (::ws/children node)))))

(defn ^:private get-node [ws-api node-id]
  (storage/query (:store ws-api) {::storage/type ::fetch-by-id
                                  ::ws/id        node-id}))

(defn create!
  "Creates a workspace node"
  [ws-api {::ws/keys [parent-id content]}]
  (locking ws-api
    (let [parent (when parent-id
                   (get-node ws-api parent-id))
          node (cond-> {::ws/id      (uuids/random)
                        ::ws/index   insertion-index
                        ::ws/content content}
                 parent (assoc ::ws/parent-id parent-id))
          payload (if parent
                    {::ws/id       parent-id
                     ::ws/children [node]}
                    node)]
      (storage/execute! (:store ws-api)
                        (assoc payload ::storage/type ::save!))
      (when-let [txs (seq (reorder ws-api parent-id))]
        (apply storage/execute! (:store ws-api) txs))
      (->node (get-node ws-api (::ws/id node))))))

(defn ^:private get-nodes [ws-api]
  (storage/query (:store ws-api)
                 {::storage/type ::select-by-parent-id}))

(defn get-tree
  "Fetches the workspace tree"
  [ws-api]
  (->nodes (get-nodes ws-api)))

(defn delete!
  "Deletes a workspace node and any children recursively"
  [ws-api node-id]
  (locking ws-api
    (when-let [node (get-node ws-api node-id)]
      (storage/execute! (:store ws-api)
                        {::storage/type ::delete!
                         ::ws/id        node-id})
      (when-let [txs (seq (reorder ws-api (::ws/parent-id node)))]
        (apply storage/execute! (:store ws-api) txs))
      nil)))

(defn update!
  "Modifies or moves a workspace node"
  [ws-api node-id {::ws/keys [content parent-id prev-sibling-id] :or {parent-id ::same}}]
  (locking ws-api
    (when-let [{curr-parent-id ::ws/parent-id :as node} (get-node ws-api node-id)]
      (when (and curr-parent-id (has-ancestor? node parent-id))
        (throw (ex-info "cyclic workflows not allowed" {})))
      (let [same-parent? (= ::same parent-id)
            next-parent (cond
                          (nil? parent-id) {::ws/children (get-nodes ws-api)}
                          (not same-parent?) (get-node ws-api parent-id)
                          :else (get-node ws-api curr-parent-id))
            next-parent-id (::ws/id next-parent)
            sibling (when prev-sibling-id
                      (->> (::ws/children next-parent)
                           (filter (comp #{prev-sibling-id} ::ws/id))
                           first))
            next-node (cond-> {::storage/type ::save!
                               ::ws/id        node-id}
                        content
                        (assoc ::ws/content content)

                        (and (not same-parent?) (not= curr-parent-id next-parent-id))
                        (assoc ::ws/parent-id next-parent-id
                               ::ws/parent-id!remove curr-parent-id)

                        (and (nil? sibling) (not same-parent?))
                        (assoc ::ws/index -1)

                        (some? sibling)
                        (assoc ::ws/index (+ (::ws/index sibling) 0.5))

                        (nil? next-parent-id)
                        (dissoc ::ws/parent-id))
            txs (cond-> []
                  (and (some? curr-parent-id) (not same-parent?))
                  (conj {::storage/type ::remove-from-parent!
                         ::ws/parent-id curr-parent-id
                         :brainard/ref  (:brainard/ref node)})

                  (some? next-parent-id)
                  (conj {::storage/type ::save!
                         ::ws/id        next-parent-id
                         ::ws/children  [(select-keys next-node
                                                      #{::ws/id
                                                        ::ws/content
                                                        ::ws/parent-id
                                                        ::ws/index})]})

                  (nil? next-parent-id)
                  (conj next-node))]
        (apply storage/execute! (:store ws-api) txs)
        (when-let [txs (seq (concat (reorder ws-api curr-parent-id)
                                    (when (not= curr-parent-id next-parent-id)
                                      (reorder ws-api next-parent-id))))]
          (apply storage/execute! (:store ws-api) txs))
        (->node (get-node ws-api node-id))))))
