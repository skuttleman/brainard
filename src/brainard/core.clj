(ns brainard.core
  "brainard: 'cause absent-minded people need help 'membering junk"
  (:gen-class)
  (:require
    [brainard.common.utils.logger :as log]
    [brainard.infra.services.system :as sys]
    [duct.core :as duct]
    [integrant.core :as ig]
    [nrepl.server :as nrepl]))

(declare system)

(defn -main
  "Entry point for building/running the `brainard` web application from the command line.
   Runs an nREPL server because of course it does."
  [& _]
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
  (sys/start! "duct.edn")
  (ig/halt! system)

  ;; reset db
  (let [db-name "brainard"]
    (require 'datomic.client.api)
    (datomic.client.api/delete-database (:brainard.datomic/client system) {:db-name db-name})
    (alter-var-root #'system (fn [sys]
                               (ig/halt! sys)
                               (spit (format ".datomic.%s.log" db-name) "")
                               (sys/start! "duct.edn")))))
