(ns brainard.infra.routes.base
  (:require
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.response :as routes.res]
    [ring.middleware.resource :as ring.res]
    [ring.util.mime-type :as ring.mime]))

(defmethod iroutes/req->input :default
  [{:brainard/keys [route] :as req}]
  (or (:body req)
      (merge (:route-params route)
             (:query-params route))))

(defmethod iroutes/handler :default
  [_]
  (routes.res/->response 404 "Not found" {"content-type" "plain/text"}))

(defmethod iroutes/handler [:any :routes.api/not-found]
  [_]
  (routes.res/->response 404 (routes.res/errors "Not found" :UNKNOWN_API)))

(defmethod iroutes/handler [:get :routes.resources/asset]
  [{:keys [uri] :as req}]
  (let [content-type (or (ring.mime/ext-mime-type uri)
                         "text/plain")]
    (some-> req
            (ring.res/resource-request "public")
            (assoc-in [:headers "content-type"] content-type))))
