(ns brainard.infra.system.daemons
  (:require
    [brainard.api.core :as api]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.logger :as log]
    [brainard.api.utils.uuids :as uuids]
    [brainard.api.events.core :as events]))

(defn cleanup-orphaned-artifacts!
  "Cleanup all objects in the object store that have no corresponding
   entity in the data store."
  [store obj-store]
  (try
    (let [s3-keys (->> (istorage/read obj-store
                                      {:op :ListObjectsV2})
                       :Contents
                       (map (comp uuids/->uuid :Key)))
          db-keys (istorage/read store
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
            (log/infof ".. %s orphaned artifacts cleaned" (count orphaned-ids)))
        (log/debug "no orphaned artifacts to clean")))
    (catch InterruptedException ex
      (throw ex))
    (catch Throwable ex
      (log/error ex "Failed to cleanup orphaned S3 objects"))))

(defn update-buzz!
  "Broadcasts relevant notes to all connections."
  [apis events timestamp]
  (let [notes (api/invoke-api :api.notes/relevant apis {:timestamp timestamp})]
    (events/broadcast! events [:notes/relevant {:data notes}])))
