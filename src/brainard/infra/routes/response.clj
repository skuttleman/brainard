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
  ([message]
   (errors message nil))
  ([message code]
   (errors message code nil))
  ([message code params]
   {:errors [(cond-> (assoc params :message message)
               code (assoc :code code))]}))
