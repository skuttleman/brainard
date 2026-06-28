(ns brainard.infra.system.daemons
  (:require
   [brainard.api.core :as api]
   [brainard.api.storage.interfaces :as istorage]
   [brainard.api.utils.logger :as log]
   [brainard.api.events.core :as events]
   [cljc.java-time.instant :as inst]
   [cljc.java-time.temporal.chrono-unit :as cu]
   [slag.utils.uuids :as uuids])
  (:import (java.util Date)))

(defmacro ^:private try-log [msg & body]
  `(try
     ~@body
     (catch InterruptedException ex#
       (throw ex#))
     (catch Throwable ex#
       (log/error ex# ~msg))))

(defn cleanup-orphaned-artifacts!
  "Cleanup all objects in the object store that have no corresponding
   entity in the data store."
  [store obj-store]
  (try-log "Failed to cleanup orphaned S3 objects"
    (let [s3-keys (->> (istorage/read obj-store {:op :ListObjectsV2})
                       :Contents
                       (map (comp uuids/->uuid :Key)))
          db-keys (istorage/read store {:query    '[:find ?id
                                                    :in $ [?id ...]
                                                    :where
                                                    [?e :attachments/id ?id]]
                                        :args     [s3-keys]
                                        :xform    (map first)
                                        :history? true})]
      (if-let [orphaned-ids (seq (remove (set db-keys) s3-keys))]
        (let [objs (map #(hash-map :Key (str %)) orphaned-ids)]
          (log/infof "cleaning up %s orphaned artifacts ..." (count orphaned-ids))
          (istorage/write! obj-store [{:op      :DeleteObjects
                                       :request {:Delete {:Objects objs}}}])
          (log/infof "... %s orphaned artifacts cleaned" (count orphaned-ids)))
        (log/debug "no orphaned artifacts to clean")))))

(defn update-buzz!
  "Broadcasts relevant notes to all connections."
  [apis events timestamp]
  (try-log "Failed to broadcast buzz events"
    (let [notes (api/invoke-api :api.notes/relevant apis {:timestamp timestamp})]
      (events/broadcast! events :notes/relevant {:data notes}))))

(defn delete-archived-notes!
  [store]
  (try-log "Failed to permanently delete archived notes"
    (let [cutoff (-> (inst/now)
                     (inst/minus 30 cu/days)
                     Date/from)
          note-ids (istorage/read store
                                  {:query '[:find ?id
                                            :in $ ?cutoff
                                            :where
                                            [?e :notes/id ?id]
                                            [?e :notes/archived? true ?tx]
                                            [?tx :db/txInstant ?at]
                                            [(< ?at ?cutoff)]]
                                   :args [cutoff]
                                   :xform (map first)})
          schedule-ids (when (seq note-ids)
                         (istorage/read store
                                        {:query '[:find ?id
                                                  :in $ [?note-id ...]
                                                  :where
                                                  [?e :schedules/note-id ?note-id]
                                                  [?e :schedules/id ?id]]
                                         :args [note-ids]
                                         :xform (map first)}))]
      (if (seq note-ids)
        (do (log/infof "permanently deleting %s archived notes" (count note-ids))
            (istorage/write! store (concat (map #(do [:db/retractEntity [:notes/id %]]) note-ids)
                                           (map #(do [:db/retractEntity [:schedules/id %]]) schedule-ids)))
            (log/infof "... archived notes deleted" (count note-ids)))
        (log/debug "no archived notes to cleanup")))))
