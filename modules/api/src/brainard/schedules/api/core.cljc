(ns brainard.schedules.api.core
  (:require
    [brainard.api.utils.uuids :as uuids]
    [brainard.schedules.api.relevancy :as relevancy]
    [brainard.storage.core :as storage]))

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

(defn create! [schedules-api schedule]
  (let [schedule (clean-schedule schedule (uuids/random))]
    (storage/execute! (:store schedules-api) (assoc schedule ::storage/type ::save!))
    schedule))

(defn delete! [schedule-api schedule-id]
  (storage/execute! (:store schedule-api) {::storage/type ::delete!
                                           :schedules/id  schedule-id})
  nil)

(defn relevant-schedules [schedules-api timestamp]
  (let [{:keys [weekday month day week-index]} (relevancy/from timestamp)]
    (storage/query (:store schedules-api)
                   {::storage/type              ::schedules
                    :schedules/after-timestamp  timestamp
                    :schedules/before-timestamp timestamp
                    :schedules/day              day
                    :schedules/month            month
                    :schedules/weekday          weekday
                    :schedules/week-index       week-index})))

(defn get-by-note-id [schedules-api note-id]
  (storage/query (:store schedules-api)
                 {::storage/type     ::get-by-note-id
                  :schedules/note-id note-id}))
