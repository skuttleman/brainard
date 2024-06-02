(ns brainard.schedules.api.core
  (:require
    [brainard.api.utils.uuids :as uuids]
    [brainard.schedules.api.relevancy :as relevancy]
    [brainard.api.storage.core :as storage]))

(defn ^:private clean-schedule [schedule schedule-id]
  (-> schedule
      (select-keys #{:schedules/note-id
                     :schedules/after-timestamp
                     :schedules/before-timestamp
                     :schedules/day
                     :schedules/month
                     :schedules/weekday
                     :schedules/week-index})
      (assoc :schedules/id schedule-id)))

(defn create!
  "Creates a schedule"
  [schedules-api schedule]
  (let [schedule (clean-schedule schedule (uuids/random))]
    (storage/execute! (:store schedules-api) (assoc schedule ::storage/type ::save!))
    schedule))

(defn delete!
  "Deletes a schedule"
  [schedule-api schedule-id]
  (storage/execute! (:store schedule-api) {::storage/type ::delete!
                                           :schedules/id  schedule-id})
  nil)

(defn delete-for-note!
  "Deletes schedules associated with a note"
  [schedule-api note-id]
  (when-let [schedules (seq (storage/query (:store schedule-api)
                                           {::storage/type     ::get-by-note-id
                                            :schedules/note-id note-id}))]
    (apply storage/execute!
           (:store schedule-api)
           (map (fn [{:schedules/keys [id]}]
                  {::storage/type ::delete!
                   :schedules/id  id})
                schedules)))
  nil)

(defn relevant-schedules
  "Retrieves relevant schedules for a timestamp"
  [schedules-api timestamp]
  (let [{:keys [weekday month day week-index]} (relevancy/from timestamp)]
    (storage/query (:store schedules-api)
                   {::storage/type              ::schedules
                    :schedules/after-timestamp  timestamp
                    :schedules/before-timestamp timestamp
                    :schedules/day              day
                    :schedules/month            month
                    :schedules/weekday          weekday
                    :schedules/week-index       week-index})))

(defn get-by-note-id
  "Retrieves schedules associated with a note"
  [schedules-api note-id]
  (storage/query (:store schedules-api)
                 {::storage/type     ::get-by-note-id
                  :schedules/note-id note-id}))
