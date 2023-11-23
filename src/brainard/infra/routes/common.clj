(ns brainard.infra.routes.common)

(def handler-hierarchy
  (-> (make-hierarchy)
      (derive :get ::any)
      (derive :post ::any)
      (derive :put ::any)
      (derive :patch ::any)
      (derive :delete ::any)))

(defmulti handler
          "Defines a round handler"
          (fn [{:keys [request-method] :brainard/keys [route]}]
            [request-method (:handler route)])
          :hierarchy
          #'handler-hierarchy)

(defmethod handler [::any :routes.api/not-found]
  [_]
  {:status 404
   :body "[:not :found]"})

(defmethod handler [::any :routes/not-found]
  [_]
  {:status 404
   :headers {"content-type" "plain/text"}
   :body "Not found"})
