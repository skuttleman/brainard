(ns brainard.infra.routes.base
  (:require
    [brainard.common.store.core :as store]
    [brainard.common.views.pages.core :as pages]
    [brainard.infra.routes.html :as routes.html]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.response :as routes.res]
    [ring.middleware.resource :as ring.res]
    [ring.util.mime-type :as ring.mime]))

(defmethod iroutes/req->input :default
  [req]
  (:body req))

(defmethod iroutes/handler :default
  [_]
  (routes.res/->response 404 "Not found" {"content-type" "plain/text"}))

(defmethod iroutes/handler [:any :routes.api/not-found]
  [_]
  (routes.res/->response 404 (routes.res/errors "Not found" :UNKNOWN_API)))

(defmethod iroutes/handler [:get :routes/ui]
  [{:brainard/keys [route]}]
  (routes.res/->response 200
                         (routes.html/render [pages/page (store/create) route])
                         {"content-type" "text/html"}))

(defmethod iroutes/handler [:get :routes.resources/asset]
  [req]
  (let [content-type (or (ring.mime/ext-mime-type (:uri req))
                         "text/plain")]
    (some-> req
            (ring.res/resource-request "public")
            (assoc-in [:headers "content-type"] content-type))))
