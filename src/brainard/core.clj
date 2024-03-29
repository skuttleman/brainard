(ns brainard.core
  "brainard: 'cause absent-minded people need help 'membering stuff"
  (:gen-class)
  (:require
    [duct.core :as duct]
    [integrant.core :as ig]
    brainard.infra.system
    brainard.workspace.infra.store))

(defn start!
  "Starts a duct component system from a configuration expressed in an `edn` file."
  [config-file profiles]
  (duct/load-hierarchy)
  (-> config-file
      duct/resource
      duct/read-config
      (duct/prep-config profiles)
      (ig/init [:duct/daemon])))

(defn -main
  "Entry point for building/running the `brainard` web application from the command line.
   Runs an nREPL when `NREPL_PORT` env var is set."
  [& _]
  (duct/await-daemons (start! "duct/prod.edn" [:duct.profile/base :duct.profile/prod])))
