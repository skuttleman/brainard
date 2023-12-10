(ns brainard.infra.stores.schedules
  (:require
    [brainard.api.schedules.interfaces :as isched]
    [brainard.infra.services.datomic :as datomic]))

(def ^:private ^:const select
  '[:find (pull ?e [:schedules/id
                    :schedules/note-id
                    :schedules/before-timestamp
                    :schedules/after-timestamp
                    :schedules/month
                    :schedules/day
                    :schedules/weekday
                    :schedules/week-index])
    :in $])

(defn ^:private save! [{:keys [datomic-conn]} schedule]
  (datomic/transact! datomic-conn {:tx-data [schedule]}))

(defn ^:private delete! [{:keys [datomic-conn]} schedule-id]
  (datomic/transact! datomic-conn {:tx-data [[:db/retractEntity [:schedules/id schedule-id]]]}))

(defn ^:private get-schedules [{:keys [datomic-conn]} filters]
  (let [{:schedules/keys [after-timestamp before-timestamp day month week-index weekday]} filters
        query (into select
                    '[?weekday ?month ?day ?week-idx ?after ?before
                      :where
                      [?e :schedules/id]
                      [(get-else $ ?e :schedules/weekday ?weekday) ?wd]
                      [(= ?wd ?weekday)]
                      [(get-else $ ?e :schedules/month ?month) ?m]
                      [(= ?m ?month)]
                      [(get-else $ ?e :schedules/day ?day) ?d]
                      [(= ?d ?day)]
                      [(get-else $ ?e :schedules/week-index ?week-idx) ?wi]
                      [(= ?wi ?week-idx)]
                      [(get-else $ ?e :schedules/after-timestamp ?after) ?ats]
                      [(<= ?ats ?after)]
                      [(get-else $ ?e :schedules/before-timestamp ?before) ?bts]
                      [(>= ?bts ?before)]])]
    (map first (datomic/query datomic-conn
                              query
                              weekday
                              month
                              day
                              week-index
                              after-timestamp
                              before-timestamp))))

(defn ^:private get-by-note-id [{:keys [datomic-conn]} note-id]
  (datomic/query datomic-conn
                 (into select
                       '[?note-id
                         :where
                         [?e :schedules/note-id ?note-id]])
                 note-id))

(defn create-store
  "Creates a schedules store which implements [[isched/IScheduleStore]]"
  [this]
  (with-meta this
             {`isched/save!          #'save!
              `isched/delete!        #'delete!
              `isched/get-schedules  #'get-schedules
              `isched/get-by-note-id #'get-by-note-id}))
