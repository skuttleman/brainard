(ns brainard.infra.routes.common
  (:require
    [brainard.infra.routes.html :as html]
    [ring.middleware.resource :as ring.res]
    [clojure.string :as string]))

(def routing-hierarchy
  (-> (make-hierarchy)
      (derive :get :any)
      (derive :post :any)
      (derive :put :any)
      (derive :patch :any)
      (derive :delete :any)
      (derive :routes.ui/home :routes/ui)
      (derive :routes.resources/js :routes.resources/asset)
      (derive :routes.resources/css :routes.resources/asset)))

(defn router [{:keys [request-method] :brainard/keys [route]}]
  [request-method (:handler route)])

(defmulti handler
          "Defines an HTTP route handler"
          router
          :hierarchy
          #'routing-hierarchy)

(defmethod handler [:any :routes.api/not-found]
  [_]
  {:status 404
   :body   {:errors [{:message "Not found"
                      :code    :UNKNOWN_API}]}})

(defmethod handler [:any :routes/not-found]
  [_]
  {:status  404
   :headers {"content-type" "plain/text"}
   :body    "Not found"})

(defmethod handler [:get :routes/ui]
  [_]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    (html/render "html.edn")})

(defmethod handler [:get :routes.resources/asset]
  [{:keys [uri] :as req}]
  (some-> (ring.res/resource-request req "public")
          (assoc-in [:headers "content-type"]
                    (cond
                      (string/ends-with? uri ".js") "application/javascript"
                      (string/ends-with? uri ".css") "text/css"
                      :else "text/plain"))))

(defmulti coerce
          "Defines an HTTP route coercer which gathers relevant data under :brainard/input"
          router
          :hierarchy
          #'routing-hierarchy)

(defmethod coerce :default
  [req]
  (:body req))

(defn coerce-input [req]
  (assoc req :brainard/input (coerce req)))
