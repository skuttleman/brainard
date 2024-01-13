(ns brainard.common.api.schedules.core
  (:require
    [brainard.common.api.schedules.interfaces :as isched]
    [brainard.common.utils.uuids :as uuids]
    [brainard.common.api.schedules.relevancy :as relevancy]))

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
    (isched/save! (:store schedules-api)
                  schedule)
    schedule))

(defn delete! [schedule-api schedule-id]
  (isched/delete! (:store schedule-api) schedule-id)
  nil)

(defn relevant-schedules [schedules-api timestamp]
  (let [{:keys [weekday month day week-index]} (relevancy/from timestamp)]
    (isched/get-schedules (:store schedules-api)
                          {:schedules/after-timestamp  timestamp
                           :schedules/before-timestamp timestamp
                           :schedules/day              day
                           :schedules/month            month
                           :schedules/weekday          weekday
                           :schedules/week-index       week-index})))

(defn get-by-note-id [schedules-api note-id]
  (isched/get-by-note-id (:store schedules-api) note-id))
