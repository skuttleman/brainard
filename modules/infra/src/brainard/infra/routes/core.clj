(ns brainard.infra.routes.core
  (:require
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.middleware :as mw]
    [ring.middleware.keyword-params :as ring.kw-params]
    [ring.middleware.params :as ring.params]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries
    brainard.infra.routes.base
    brainard.infra.routes.notes))

(defn ^:private asset? [{:keys [uri]}]
  (re-matches #"^/(js|css|img|favicon\.ico).*" uri))

(defn req->input
  "Extracts request input from (potentially multiple places in) the HTTP request."
  [req]
  (iroutes/req->input req))

(def handler
  "Handles all HTTP requests through the webserver."
  (-> iroutes/handler
      mw/with-spec-validation
      (mw/with-input {:req->input req->input})
      ring.kw-params/wrap-keyword-params
      ring.params/wrap-params
      mw/with-edn
      mw/with-routing
      mw/with-error-handling
      (mw/with-logging {:xform (remove asset?)})))
