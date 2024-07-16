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

(defmethod istorage/->input ::api.apps/get-app
  [{app-id :applications/id}]
  {:query '[:find (pull ?e [*])
            :in $ ?app-id
            :where
            [?e :applications/id ?app-id]]
   :args  [app-id]
   :only? true
   :xform (map first)})
