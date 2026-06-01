(ns brainard.test
  (:require
    [brainard :as-alias b]
    [brainard.infra.routes.core :as routes]
    [integrant.core :as ig]))

(defn ^:private with-test-middleware [handler upload-limit]
  (fn [req]
    (-> req
        (assoc ::b/env :test ::b/file-limit-bytes upload-limit)
        handler)))

(defmethod ig/init-key :brainard.web/test-handler
  [_ {:keys [upload-limit]}]
  (with-test-middleware routes/be-handler upload-limit))
