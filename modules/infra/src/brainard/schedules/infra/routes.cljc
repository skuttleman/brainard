(ns brainard.schedules.infra.routes
  (:require
    [brainard :as-alias b]
    [brainard.api.core :as api]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.response :as routes.res]))

(defmethod iroutes/handler [:post :routes.api/schedules]
  [{::b/keys [apis input]}]
  (let [results (api/create-schedule! apis input)]
    (routes.res/->response 201 {:data results})))

(defmethod iroutes/handler [:delete :routes.api/schedule]
  [{::b/keys [apis input]}]
  (api/delete-schedule! apis (:schedules/id input))
  (routes.res/->response 204))
