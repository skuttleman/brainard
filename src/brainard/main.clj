(ns brainard.main
  "brainard: 'cause absent-minded people need help 'membering stuff"
  (:gen-class)
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.logger :as log]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.utils.edn :as edn]
    [clojure.java.io :as io]
    [duct.core :as duct]
    [duct.core.env :as duct.env]
    [integrant.core :as ig]
    brainard.infra.system))

(def ^:const ^long twelve-hours (* 1000 60 60 12))

(defn ^:private with-env-file [env]
  (let [f (io/file ".env")]
    (merge env (when (.exists f)
                 (edn/read f)))))

(defn cleanup-orphaned-artifacts!
  "Remove orphaned artifacts from object storage that are no longer referenced in the database."
  [sys]
  (try
    (let [db-conn (val (ig/find-derived-1 sys :brainard/storage))
          obj-store (val (ig/find-derived-1 sys :brainard/obj-storage))
          s3-keys (->> (istorage/read obj-store
                                      {:op :ListObjectsV2})
                       :Contents
                       (map (comp uuids/->uuid :Key)))
          db-keys (istorage/read db-conn
                                 {:query    '[:find ?id
                                              :in $ [?id ...]
                                              :where
                                              [?e :attachments/id ?id]]
                                  :args     [s3-keys]
                                  :xform    (map first)
                                  :history? true})]
      (if-let [orphaned-ids (seq (remove (set db-keys) s3-keys))]
        (do (log/infof "cleaning up %s orphaned artifacts ..." (count orphaned-ids))
            (istorage/read obj-store
                           {:op      :DeleteObjects
                            :request {:Delete {:Objects (map #(hash-map :Key (str %))
                                                             orphaned-ids)}}})
            (log/info "... cleaned"))
        (log/debug "no orphaned artifacts to clean")))
    (catch Throwable ex
      (log/error ex "Failed to cleanup orphaned s3 objects"))))

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
  (let [sys (start! "duct/prod.edn" [:duct.profile/base :duct.profile/prod])
        thread (doto (Thread. (fn []
                                (cleanup-orphaned-artifacts! sys)
                                (Thread/sleep twelve-hours)
                                (recur)))
                 .start)]
    (duct/add-shutdown-hook ::stop-thread #(.interrupt thread))
    (duct/await-daemons sys)))
