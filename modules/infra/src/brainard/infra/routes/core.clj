(ns brainard.infra.routes.core
  (:require
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.middleware :as mw]
    [brainard.infra.routes.response :as routes.res]
    [brainard.infra.routes.ui :as routes.ui]
    [ring.middleware.keyword-params :as ring.kw-params]
    [ring.middleware.params :as ring.params]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries
    brainard.infra.routes.base
    brainard.infra.routes.notes))

(defn ^:private asset? [{:keys [uri]}]
  (re-matches #"^/(js|css|img|favicon\.ico).*" uri))

(def handler
  "Handles all HTTP requests through the webserver."
  (-> iroutes/handler
      mw/with-spec-validation
      (mw/with-input {:req->input iroutes/req->input})
      ring.kw-params/wrap-keyword-params
      ring.params/wrap-params
      mw/with-edn
      mw/with-routing
      mw/with-error-handling
      (mw/with-logging {:xform (remove asset?)})))
