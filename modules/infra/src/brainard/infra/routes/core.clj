(ns brainard.infra.routes.core
  (:require
    [brainard.infra.routes.middleware :as mw]
    [ring.middleware.keyword-params :as ring.kw-params]
    [ring.middleware.params :as ring.params]
    [brainard.common.routes.base :as base]
    brainard.common.routes.notes
    brainard.common.routes.schedules
    brainard.infra.routes.ui))

(defn ^:private asset? [req]
  (re-matches #"^/(js|css|favicon).*$" (:uri req)))

(def handler
  "Handles all HTTP requests through the webserver."
  (-> base/handler
      ring.kw-params/wrap-keyword-params
      ring.params/wrap-params
      mw/with-edn
      mw/with-error-handling
      (mw/with-logging {:xform (remove asset?)})))
