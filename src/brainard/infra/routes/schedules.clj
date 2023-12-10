(ns brainard.infra.routes.schedules
  (:require
    [brainard.api.core :as api]
    [brainard.infra.routes.interfaces :as iroutes])
  (:import
    (java.util Date)))

(defmethod iroutes/handler [:get :routes.api/schedules]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (api/relevant-notes apis (Date.))}})

(defmethod iroutes/handler [:post :routes.api/schedules]
  [{:brainard/keys [apis input]}]
  {:status 201
   :body   {:data (api/create-schedule! apis input)}})

(defmethod iroutes/handler [:delete :routes.api/schedule]
  [{:brainard/keys [apis input]}]
  (api/delete-schedule! apis (:schedules/id input))
  {:status 204})
