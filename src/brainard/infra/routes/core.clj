(ns brainard.infra.routes.core
  (:require
    [brainard.infra.routes.common :as routes.common]
    [brainard.infra.routes.middleware :as mw]
    [clojure.string :as string]
    [ring.middleware.keyword-params :as ring.kw-params]
    [ring.middleware.params :as ring.params]
    brainard.infra.routes.notes))

(defn ^:private route-filter [{:keys [uri]}]
  (or (string/starts-with? uri "/js")
      (string/starts-with? uri "/css")
      (string/starts-with? uri "/favicon.ico")))

(def handler (-> routes.common/handler
                 mw/with-spec-validation
                 ring.kw-params/wrap-keyword-params
                 ring.params/wrap-params
                 mw/with-edn
                 mw/with-routing
                 mw/with-error-handling
                 (mw/with-logging {:xform (remove route-filter)})))
