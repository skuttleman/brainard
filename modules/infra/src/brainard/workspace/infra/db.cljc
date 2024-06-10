(ns brainard.workspace.infra.db
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.workspace.api.core :as api.ws]
    [workspace-nodes :as-alias ws]))

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

(defmethod istorage/->input ::api.ws/save!
  [{::ws/keys [id parent-id!remove] :as node}]
  (cond-> [(select-keys node #{::ws/id
                               ::ws/index
                               ::ws/parent-id
                               ::ws/content
                               ::ws/children})]
    parent-id!remove (conj [:db/retract [::ws/id id] ::ws/parent-id parent-id!remove])))

(defmethod istorage/->input ::api.ws/delete!
  [{::ws/keys [id]}]
  [[:db/retractEntity [::ws/id id]]])

(defmethod istorage/->input ::api.ws/remove-from-parent!
  [{parent-id ::ws/parent-id ref :brainard/ref}]
  [[:db/retract [::ws/id parent-id] ::ws/children ref]])
