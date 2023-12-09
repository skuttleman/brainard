(ns brainard.infra.routes.schedules
  (:require
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.api.schedules.core :as sched])
  (:import
    (java.util Date)))

(defmethod iroutes/handler [:get :routes.api/schedules]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (sched/relevant-notes (:schedules apis) (Date.))}})

(defmethod iroutes/handler [:post :routes.api/schedules]
  [{:brainard/keys [apis input]}]
  {:status 201
   :body   {:data (sched/create! (:schedules apis) input)}})

(defmethod iroutes/handler [:delete :routes.api/schedule]
  [{:brainard/keys [apis input]}]
  (sched/delete! (:schedules apis) (:schedules/id input))
  {:status 204})
