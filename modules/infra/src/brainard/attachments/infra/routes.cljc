(ns brainard.attachments.infra.routes
  (:require
   [brainard.infra.routes.interfaces :as iroutes]
   [slag.utils.uuids :as uuids]))

(defmethod iroutes/req->input [:post :routes.api/attachments]
  [req]
  {:request-id  (uuids/->uuid (get-in req [:headers "x-request-id"]))
   :attachments (for [{:keys [content-type filename stream]} (-> req :multipart-params :files)]
                  {:attachments/content-type content-type
                   :attachments/stream       stream
                   :attachments/filename     filename})})
