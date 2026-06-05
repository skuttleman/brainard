(ns brainard.notifications.infra.routes
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.logger :as log]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.api.notifications.interfaces :as inotifications]
    [immutant.web.async :as web.async]
    [whet.core :as-alias w]))

(defmethod iroutes/handler [:get :routes.ws/connection]
  [{::b/keys [ws] :as req}]
  (let [ch-id (uuids/random)
        handler {:on-open  (fn [ch]
                             (inotifications/connect! ws ch-id ch)
                             (log/infof "ws connected: %s" ch-id))
                 :on-close (fn [_ _]
                             (inotifications/disconnect! ws ch-id)
                             (log/infof "ws disconnected: %s" ch-id))}]
    (-> req
        (web.async/as-channel handler)
        (assoc ::w/raw? true))))
