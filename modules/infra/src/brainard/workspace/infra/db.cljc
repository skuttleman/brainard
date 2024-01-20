(ns brainard.workspace.infra.db
  (:require
    [brainard.infra.db.datascript :as ds]
    [brainard.workspace.api.interfaces :as iwork]
    [clojure.walk :as walk]))

(defn ^:private remove-db-ids [result]
  (walk/postwalk (fn [x]
                   (cond-> x (map? x) (dissoc :db/id)))
                 result))

(defn ^:private get-all [{:keys [ds-client]}]
  (->> (ds/query ds-client
                 '[:find (pull ?e [:workspace-nodes/id
                                   :workspace-nodes/parent-id
                                   :workspace-nodes/relative-order
                                   :workspace-nodes/data
                                   :workspace-nodes/nodes])
                   :where
                   [?e :workspace-nodes/id]
                   [(missing? $ ?e :workspace-nodes/parent-id)]])
       (map (comp remove-db-ids first))))

(defn ^:private get-by-id [{:keys [ds-client]} node-id]
  (->> (ds/query ds-client
                 '[:find (pull ?e [:workspace-nodes/id
                                   :workspace-nodes/parent-id
                                   :workspace-nodes/relative-order
                                   :workspace-nodes/data
                                   :workspace-nodes/nodes])
                   :in $ ?id
                   :where [?e :workspace-nodes/id ?id]]
                 node-id)
       (map (comp remove-db-ids first))
       first))

(defn ^:private save! [{:keys [ds-client]} node]
  (ds/transact! ds-client [node]))

(defn ^:private delete! [{:keys [ds-client]} node-id]
  (ds/transact! ds-client [[:db/retractEntity [:workspace-nodes/id node-id]]]))

(defn ^:private get-child [ds-client node-id]
  (ffirst (ds/query ds-client
                    '[:find (pull ?e [:db/id :workspace-nodes/parent-id])
                      :in $ ?id
                      :where [?e :workspace-nodes/id ?id]]
                    node-id)))

(defn ^:private detach-node! [{:keys [ds-client]} node-id]
  (let [{parent-id :workspace-nodes/parent-id child-id :db/id} (get-child ds-client node-id)
        tx (cond-> [[:db/retract [:workspace-nodes/id parent-id] :workspace-nodes/nodes child-id]]
             parent-id
             (conj [:db/retract [:workspace-nodes/id node-id] :workspace-nodes/parent-id parent-id]))]
    (ds/transact! ds-client tx)))

(defn ^:private move-node! [{:keys [ds-client]} old-parent-id new-parent-id node-id]
  (let [child (get-child ds-client node-id)]
    (ds/transact! ds-client [[:db/retract [:workspace-nodes/id old-parent-id] :workspace-nodes/nodes child]
                             {:workspace-nodes/id new-parent-id :workspace-nodes/nodes child}
                             {:workspace-nodes/id node-id :workspace-nodes/parent-id new-parent-id}])))

(defn create-store
  "Creates a workspace store which implements the interfaces in [[iwork]]."
  [this]
  (with-meta this
             {`iwork/save!        #'save!
              `iwork/delete!      #'delete!
              `iwork/detach-node! #'detach-node!
              `iwork/move-node!   #'move-node!
              `iwork/get-all      #'get-all
              `iwork/get-by-id    #'get-by-id}))
