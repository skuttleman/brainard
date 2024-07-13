(ns brainard.infra.routes.response)

(defn ->response
  "Generates a ring-compatible HTTP response map"
  ([status]
   (->response status nil))
  ([status body]
   (->response status body nil))
  ([status body headers]
   (cond-> {:status status}
     body (assoc :body body)
     headers (assoc :headers headers))))

(defn errors
  "Generates an error response body."
  ([code message]
   (errors code message nil))
  ([code message params]
   {:errors [(cond-> (assoc params :message message)
               code (assoc :code code))]}))
