(ns brainard.applications.infra.db
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.applications.api.core :as api.apps]))

(defmethod istorage/->input ::api.apps/create!
  [app]
  [(select-keys app #{:applications/id
                      :applications/company
                      :applications/details
                      :applications/job-title
                      :applications/state})])

(defmethod istorage/->input ::api.apps/update!
  [app]
  [(select-keys app #{:applications/id
                      :applications/company
                      :applications/details
                      :applications/job-title})])

(defmethod istorage/->input ::api.apps/get-app
  [{app-id :applications/id}]
  {:query '[:find (pull ?e [*])
            :in $ ?app-id
            :where
            [?e :applications/id ?app-id]]
   :args  [app-id]
   :only? true
   :xform (map first)})

(defmethod istorage/->input ::api.apps/get-apps
  [_]
  {:query '[:find (pull ?e [*]) (max ?at)
            :where
            [?e :applications/id _]
            [?e _ _ ?tx]
            [?tx :db/txInstant ?at]]
   :xform (map (fn [[app updated-at]]
                 (assoc app :applications/updated-at updated-at)))})
