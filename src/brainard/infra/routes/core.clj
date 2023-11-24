(ns brainard.infra.routes.core
  (:require
    [brainard.infra.routes.common :as routes.common]
    [brainard.infra.routes.middleware :as mw]
    [ring.middleware.keyword-params :as ring.kw-params]
    [ring.middleware.params :as ring.params]
    brainard.infra.routes.notes))

(def handler (-> routes.common/handler
                 mw/with-spec-validation
                 ring.kw-params/wrap-keyword-params
                 ring.params/wrap-params
                 mw/with-edn
                 mw/with-routing
                 mw/with-error-handling))
