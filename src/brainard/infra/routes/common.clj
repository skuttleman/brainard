(ns brainard.infra.routes.common)

(def routing-hierarchy
  (-> (make-hierarchy)
      (derive :get ::any)
      (derive :post ::any)
      (derive :put ::any)
      (derive :patch ::any)
      (derive :delete ::any)))

(defn router [{:keys [request-method] :brainard/keys [route]}]
  [request-method (:handler route)])

(defmulti handler
          "Defines an HTTP route handler"
          router
          :hierarchy
          #'routing-hierarchy)

(defmethod handler [::any :routes.api/not-found]
  [_]
  {:status 404
   :body "[:not :found]"})

(defmethod handler [::any :routes/not-found]
  [_]
  {:status 404
   :headers {"content-type" "plain/text"}
   :body "Not found"})

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
