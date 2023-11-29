(ns brainard.infra.routes.main
  (:require
    [brainard.infra.routes.html :as html]
    [brainard.infra.routes.interfaces :as iroutes]
    [ring.middleware.resource :as ring.res]
    [clojure.string :as string]))

(defmethod iroutes/handler [:any :routes.api/not-found]
  [_]
  {:status 404
   :body   {:errors [{:message "Not found"
                      :code    :UNKNOWN_API}]}})

(defmethod iroutes/handler [:get :routes/ui]
  [_]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    (html/render "html.edn")})

(defmethod iroutes/handler [:get :routes.resources/asset]
  [{:keys [uri] :as req}]
  (some-> (ring.res/resource-request req "public")
          (assoc-in [:headers "content-type"]
                    (cond
                      (string/ends-with? uri ".js") "application/javascript"
                      (string/ends-with? uri ".css") "text/css"
                      :else "text/plain"))))

(defmethod iroutes/req->input :default
  [req]
  (:body req))
