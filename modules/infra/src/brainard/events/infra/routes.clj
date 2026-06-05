(ns brainard.events.infra.routes
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.logger :as log]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.api.events.interfaces :as ievents]
    [immutant.web.async :as web.async]
    [whet.core :as-alias w]))

(defmethod iroutes/handler [:get :routes.ws/connection]
  [{::b/keys [events] :as req}]
  (let [ch-id (uuids/random)
        handler {:on-open  (fn [ch]
                             (ievents/connect! events ch-id ch)
                             (log/infof "ws connected: %s" ch-id))
                 :on-close (fn [_ _]
                             (ievents/disconnect! events ch-id)
                             (log/infof "ws disconnected: %s" ch-id))}]
    (-> req
        (web.async/as-channel handler)
        (assoc-in [:headers "content-type"] "text/event-stream"))))
