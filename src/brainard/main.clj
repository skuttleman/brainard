(ns brainard.main
  "brainard: 'cause absent-minded people need help 'membering stuff"
  (:gen-class)
  (:require
   [brainard :as-alias b]
   [brainard.ws :as-alias bws]
   [clojure.java.io :as io]
   [duct.core :as duct]
   [duct.core.env :as duct.env]
   [integrant.core :as ig]
   [slag.utils.edn :as edn]
   brainard.infra.system.core))

(defn ^:private with-env-file [env]
  (let [f (io/file ".env")]
    (merge env (when (.exists f)
                 (edn/read f)))))

(defn start!
  "Starts a duct component system from a configuration expressed in an `edn` file."
  ([config-file profiles]
   (start! config-file profiles [:duct/daemon]))
  ([config-file profiles init-keys]
   (duct/load-hierarchy)
   (binding [duct.env/*env* (with-env-file duct.env/*env*)]
     (-> config-file
         duct/resource
         duct/read-config
         (duct/prep-config profiles)
         (ig/init init-keys)))))

(defn -main
  "Entry point for running the `brainard` web application from the command line."
  [& _]
  (duct/await-daemons (start! "duct/prod.edn" [:duct.profile/base :duct.profile/prod])))
