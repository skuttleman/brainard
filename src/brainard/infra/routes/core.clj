(ns brainard.infra.routes.core
  (:require
    [brainard.common.utils.logger :as log]
    [brainard.infra.routes.middleware :as mw]
    [ring.middleware.keyword-params :as ring.kw-params]
    [ring.middleware.params :as ring.params]
    [brainard.common.routes.base :as base]
    brainard.common.routes.notes
    brainard.common.routes.schedules
    brainard.infra.routes.ui))

(defn ^:private asset? [{:keys [uri]}]
  (re-matches #"^/(js|css|img|favicon\.ico).*" uri))

(defmacro ^:private ->dev-mw []
  (try
    (require 'ring.middleware.reload)
    (log/debug "running with dev-middleware")
    `ring.middleware.reload/wrap-reload
    (catch Throwable _
      `(fn [handler# _opts#] handler#))))

(def ^:private dev-middleware (->dev-mw))

(def handler
  "Handles all HTTP requests through the webserver."
  (-> base/handler
      ring.kw-params/wrap-keyword-params
      ring.params/wrap-params
      mw/with-edn
      mw/with-error-handling
      (mw/with-logging {:xform (remove asset?)})
      (dev-middleware {:dirs ["src"]})))
