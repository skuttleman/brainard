(ns brainard.events.infra.routes
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.logger :as log]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.api.events.interfaces :as ievents]
    [immutant.web.async :as web.async]))

(defn ^:internal ^:no-doc handle-events [{::b/keys [events] :as req} ->chan-resp send-fn]
  (let [ch-id (uuids/random)
        handler {:on-open  (fn [ch]
                             (ievents/connect! events ch-id ch)
                             (send-fn ch "event: connected\n\n")
                             (log/infof "ws connected: %s" ch-id))
                 :on-close (fn [_ _]
                             (ievents/disconnect! events ch-id)
                             (log/infof "ws disconnected: %s" ch-id))}]
    (-> req
        (->chan-resp handler)
        (assoc-in [:headers "content-type"] "text/event-stream"))))

(defmethod iroutes/handler [:get :routes.api/events]
  [req]
  (handle-events req web.async/as-channel web.async/send!))
