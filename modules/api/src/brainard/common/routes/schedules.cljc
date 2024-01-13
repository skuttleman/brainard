(ns brainard.common.routes.schedules
  (:require
    [brainard.common.api.core :as api]
    [brainard.common.routes.interfaces :as iroutes]))

(defmethod iroutes/handler [:post :routes.api/schedules]
  [{:brainard/keys [apis input]}]
  {:status 201
   :body   {:data (api/create-schedule! apis input)}})

(defmethod iroutes/handler [:delete :routes.api/schedule]
  [{:brainard/keys [apis input]}]
  (api/delete-schedule! apis (:schedules/id input))
  {:status 204})
