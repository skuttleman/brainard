(ns brainard.core
  (:require
    [brainard.common.utils.logger :as log]
    [brainard.infra.system :as sys]
    [duct.core :as duct]
    [integrant.core :as ig]
    [nrepl.server :as nrepl]))

(declare system)

(defn -main [& _]
  (let [nrepl-port (Long/parseLong (or (System/getenv "NREPL_PORT") "7300"))
        nrepl-server (do (log/info "starting nREPL server on port" nrepl-port)
                         (nrepl/start-server :bind "0.0.0.0" :port nrepl-port))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable
                               (fn []
                                 (log/info "stopping nREPL server")
                                 (nrepl/stop-server nrepl-server))))
    (def system (sys/start! "duct.edn"))
    (duct/await-daemons system)))

(comment
  (def system (sys/start! "duct.edn"))
  (ig/halt! system)
  (alter-var-root #'system (fn [sys]
                             (ig/halt! sys)
                             (sys/start! "duct.edn"))))
