(ns brainard.schedules.infra.routes
  (:require
    [brainard :as-alias b]
    [brainard.api.core :as api]
    [brainard.infra.routes.interfaces :as iroutes]))

(defmethod iroutes/handler [:post :routes.api/schedules]
  [{::b/keys [apis input]}]
  {:status 201
   :body   {:data (api/create-schedule! apis input)}})

(defmethod iroutes/handler [:delete :routes.api/schedule]
  [{::b/keys [apis input]}]
  (api/delete-schedule! apis (:schedules/id input))
  {:status 204})
