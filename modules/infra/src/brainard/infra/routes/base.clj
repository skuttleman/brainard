(ns brainard.infra.routes.base
  (:require
    [brainard.infra.routes.html :as routes.html]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.response :as routes.res]
    [ring.middleware.resource :as ring.res]
    [clojure.string :as string]))

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
  [_]
  (routes.res/->response 200 (routes.html/render "index.edn") {"content-type" "text/html"}))

(defmethod iroutes/handler [:get :routes.resources/asset]
  [{:keys [uri] :as req}]
  (let [content-type (cond
                       (string/ends-with? uri ".js") "application/javascript"
                       (string/ends-with? uri ".css") "text/css"
                       :else "text/plain")]
    (some-> req
            (ring.res/resource-request "public")
            (assoc-in [:headers "content-type"] content-type))))
