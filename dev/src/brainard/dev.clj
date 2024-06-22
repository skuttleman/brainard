(ns brainard.dev
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.logger :as log]
    [brainard.core :as core]
    [brainard.infra.routes.core :as routes]
    [duct.core :as duct]
    [integrant.core :as ig]
    [nrepl.server :as nrepl]
    [ring.middleware.reload :as ring.rel]))

(defonce system nil)

(defn -main
  "Entry point for building/running the `brainard` web application from the command line.
   Runs an nREPL when `NREPL_PORT` env var is set."
  [& _]
  (let [nrepl-port (some-> (System/getenv "NREPL_PORT") Long/parseLong)]
    (when-let [nrepl-server (when nrepl-port
                              (log/info "starting nREPL server on port" nrepl-port)
                              (nrepl/start-server :bind "0.0.0.0" :port nrepl-port))]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. ^Runnable
                                 (fn []
                                   (log/info "stopping nREPL server")
                                   (nrepl/stop-server nrepl-server)))))
    (alter-var-root #'system (constantly (core/start! "duct/dev.edn"
                                                      [:duct.profile/base :duct.profile/dev])))
    (duct/await-daemons system)))

(defmethod ig/init-key :brainard.web/dev-handler
  [_ _]
  (-> #'routes/be-handler
      ((fn [handler] (fn [req] (handler (assoc req ::b/env :dev)))))
      (ring.rel/wrap-reload {:dirs ["../defacto"
                                    "../whet"
                                    "modules/api/src"
                                    "modules/infra/src"
                                    "src"
                                    "dev"]})))

(comment
  (def system (core/start! "duct/dev.edn" [:duct.profile/base :duct.profile/dev]))
  (ig/halt! system))
