(ns brainard.workspace.infra.db
  (:require
    [brainard.storage.interfaces :as istorage]
    [brainard.workspace.api.core :as api.work]))

(defmethod istorage/->input ::api.work/get-workspace
  [_]
  {:query '[:find (pull ?e [*])
            :where
            [?e :workspace-nodes/id]
            [(missing? $ ?e :workspace-nodes/parent-id)]]
   :xform (map first)})

(defmethod istorage/->input ::api.work/get-by-id
  [{:workspace-nodes/keys [id]}]
  {:query '[:find (pull ?e [*])
            :in $ ?id
            :where [?e :workspace-nodes/id ?id]]
   :args  [id]
   :only? true
   :xform (map first)})

(defmethod istorage/->input ::api.work/save!
  [node]
  [(select-keys node #{:workspace-nodes/id
                       :workspace-nodes/parent-id
                       :workspace-nodes/data
                       :workspace-nodes/nodes})])

(defmethod istorage/->input ::api.work/delete-by-id!
  [{:workspace-nodes/keys [id]}]
  [[:db/retractEntity [:workspace-nodes/id id]]])

(defmethod istorage/->input ::api.work/detach!
  [{:workspace-nodes/keys [id parent-id] :as params}]
  (let [{:brainard/keys [ref]} params]
    [[:db/retract [:workspace-nodes/id parent-id] :workspace-nodes/nodes ref]
     [:db/retract [:workspace-nodes/id id] :workspace-nodes/parent-id parent-id]]))

(defmethod istorage/->input ::api.work/attach!
  [{:workspace-nodes/keys [id old-parent-id new-parent-id] :as params}]
  (let [{:brainard/keys [ref]} params]
    (cond-> [{:workspace-nodes/id new-parent-id :workspace-nodes/nodes ref}
             {:workspace-nodes/id id :workspace-nodes/parent-id new-parent-id}]
      old-parent-id
      (conj [:db/retract [:workspace-nodes/id old-parent-id] :workspace-nodes/nodes ref]))))
