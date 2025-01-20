(ns brainard.attachments.infra.routes
  (:require
    [brainard.infra.routes.interfaces :as iroutes]))

(defmethod iroutes/req->input [:post :routes.api/attachments]
  [req]
  (for [{:keys [content-type filename size tempfile]} (-> (:multipart-params req)
                                                     (get "files[]")
                                                     (as-> $ (cond-> $ (and (seq $) (not (sequential? $))) vector)))]
    {:attachments/content-type content-type
     :attachments/file         tempfile
     :attachments/filename     filename
     :attachments/size         size}))
