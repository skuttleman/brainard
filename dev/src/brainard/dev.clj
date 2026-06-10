(ns brainard.dev
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.logger :as log]
    [brainard.main :as main]
    [duct.core :as duct]
    [integrant.core :as ig]
    [nrepl.server :as nrepl]
    [ring.middleware.reload :as ring.rel]
    brainard.dev.s3))

(defonce system nil)

(defn ^:private dev-middleware [req]
  (-> req
      #_(assoc ::b/no-hydrate? true)))

(defn ^:private with-dev-middleware [handler]
  (fn [req]
    (-> req dev-middleware handler)))

(defmethod ig/init-key :brainard.web/dev-handler
  [_ cfg]
  (-> (ig/init-key :brainard.web/handler cfg)
      with-dev-middleware
      (ring.rel/wrap-reload {:dirs ["../defacto/core/src"
                                    "../defacto/forms/src"
                                    "../defacto/forms+/src"
                                    "../whet/src"
                                    "modules/api/src"
                                    "modules/infra/src"
                                    "src"
                                    "dev/src"]})))

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
    (let [sys (main/start! "duct/dev.edn" [:duct.profile/base :duct.profile/dev])]
      (alter-var-root #'system (constantly sys))
      (duct/await-daemons system))))

(comment
  (alter-var-root #'system (fn [sys]
                             (some-> sys ig/halt!)
                             (main/start! "duct/dev.edn" [:duct.profile/base :duct.profile/dev])))
  (ig/halt! system))
