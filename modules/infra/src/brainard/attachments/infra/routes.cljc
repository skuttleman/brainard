(ns brainard.attachments.infra.routes
  (:require
    [brainard.infra.routes.interfaces :as iroutes]))

(defmethod iroutes/req->input [:post :routes.api/attachments]
  [req]
  (for [{:keys [content-type filename stream]} (-> req :multipart-params :files)]
    {:attachments/content-type content-type
     :attachments/stream       stream
     :attachments/filename     filename}))
