(ns brainard.infra.routes.interfaces)

(def routing-hierarchy
  (-> (make-hierarchy)
      (derive :get :any)
      (derive :post :any)
      (derive :put :any)
      (derive :patch :any)
      (derive :delete :any)
      (derive :routes.resources/js :routes.resources/asset)
      (derive :routes.resources/css :routes.resources/asset)
      (derive :routes.ui/home :routes/ui)
      (derive :routes.ui/search :routes/ui)
      (derive :routes.ui/note :routes/ui)
      (derive :routes.ui/not-found :routes/ui)))

(defn router [{:keys [request-method] :brainard/keys [route]}]
  [request-method (:handler route)])

(defmulti handler
          "Defines an HTTP route handler. Dispatch value is a tuple of `[request-method handler-token]`."
          router
          :hierarchy
          #'routing-hierarchy)

(defmulti req->input
          "Defines an HTTP route coercer which gathers relevant data for the request. Defaults to the request :body."
          router
          :hierarchy
          #'routing-hierarchy)
