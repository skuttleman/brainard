(ns brainard.dev
  (:require
    [brainard.common.utils.logger :as log]
    [brainard.infra.system :as sys]
    [duct.core :as duct]
    [integrant.core :as ig]
    [nrepl.server :as nrepl]))

(declare system)

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
    (def system (sys/start! "duct.edn"))
    (duct/await-daemons system)))

(comment
  (def system (sys/start! "duct.edn"))
  (ig/halt! system))
