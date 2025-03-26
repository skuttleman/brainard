(ns brainard.core
  "brainard: 'cause absent-minded people need help 'membering stuff"
  (:gen-class)
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.utils.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [duct.core :as duct]
    [duct.core.env :as duct.env]
    [integrant.core :as ig]
    brainard.infra.system))

(defn ^:private with-env-file [env]
  (merge env (some-> (io/file ".env") edn/read)))

(defn ^:private cleanup-orphaned-artifacts! [db-conn obj-store]
  (try
    (let [s3-keys (->> (istorage/read obj-store
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
      (when-let [orphaned-ids (seq (remove (set db-keys) s3-keys))]
        (println (format "cleaning up %s orphaned artifacts ..." (count orphaned-ids)))
        (istorage/read obj-store
                       {:op      :DeleteObjects
                        :request {:Delete {:Objects (map #(hash-map :Key (str %))
                                                         orphaned-ids)}}})
        (println "... cleaned")))
    (catch Throwable ex
      (println "Failed to cleanup orphaned s3 objects")
      (pp/pprint ex))))

(defn start!
  "Starts a duct component system from a configuration expressed in an `edn` file."
  [config-file profiles]
  (duct/load-hierarchy)
  (binding [duct.env/*env* (with-env-file duct.env/*env*)]
    (let [sys (-> config-file
                  duct/resource
                  duct/read-config
                  (duct/prep-config profiles)
                  (ig/init [:duct/daemon]))
          thread (doto (Thread. (fn []
                                  (letfn [(->comp [k]
                                            (val (ig/find-derived-1 sys k)))]
                                    (cleanup-orphaned-artifacts! (->comp :brainard/storage)
                                                                 (->comp :brainard/obj-storage)))))
                   .run)]
      (duct/add-shutdown-hook ::stop-thread #(.interrupt thread))
      sys)))

(defn -main
  "Entry point for running the `brainard` web application from the command line."
  [& _]
  (duct/await-daemons (start! "duct/prod.edn" [:duct.profile/base :duct.profile/prod])))
