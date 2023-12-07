(ns brainard.infra.routes.core
  (:require
    [brainard.common.utils.logger :as log]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.middleware :as mw]
    [ring.middleware.keyword-params :as ring.kw-params]
    [ring.middleware.params :as ring.params]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries
    brainard.infra.routes.base
    brainard.infra.routes.notes
    brainard.infra.routes.ui))

(defn ^:private asset? [{:keys [uri]}]
  (re-matches #"^/(js|css|img|favicon\.ico).*" uri))

(def handler
  "Handles all HTTP requests through the webserver."
  (-> iroutes/handler
      mw/with-spec-validation
      mw/with-input
      ring.kw-params/wrap-keyword-params
      ring.params/wrap-params
      mw/with-edn
      mw/with-routing
      mw/with-error-handling
      (mw/with-logging {:xform (remove asset?)})))
