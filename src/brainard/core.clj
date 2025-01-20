(ns brainard.core
  "brainard: 'cause absent-minded people need help 'membering stuff"
  (:gen-class)
  (:require
    [brainard.infra.utils.edn :as edn]
    [clojure.java.io :as io]
    [duct.core :as duct]
    [duct.core.env :as duct.env]
    [integrant.core :as ig]
    brainard.infra.system))

(defn ^:private with-env-file [env]
  (merge env (some-> (io/file ".env") edn/read)))

(defn start!
  "Starts a duct component system from a configuration expressed in an `edn` file."
  [config-file profiles]
  (duct/load-hierarchy)
  (binding [duct.env/*env* (with-env-file duct.env/*env*)]
    (-> config-file
        duct/resource
        duct/read-config
        (duct/prep-config profiles)
        (ig/init [:duct/daemon]))))

(defn -main
  "Entry point for running the `brainard` web application from the command line."
  [& _]
  (duct/await-daemons (start! "duct/prod.edn" [:duct.profile/base :duct.profile/prod])))
