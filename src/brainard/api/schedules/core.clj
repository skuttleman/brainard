(ns brainard.api.schedules.core
  (:require
    [brainard.api.notes.core :as notes]
    [brainard.api.schedules.interfaces :as isched]
    [brainard.common.utils.uuids :as uuids]
    [brainard.api.schedules.relevancy :as relevancy]))

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

(defn ^:private select-note-ids [store timestamp]
  (let [{:keys [weekday month day week-index]} (relevancy/from timestamp)]
    (isched/get-schedules store
                          {:schedules/after-timestamp  timestamp
                           :schedules/before-timestamp timestamp
                           :schedules/day              day
                           :schedules/month            month
                           :schedules/weekday          weekday
                           :schedules/week-index       week-index})))

(defn create! [schedules-api schedule]
  (let [schedule (clean-schedule schedule (uuids/random))]
    (isched/save! (:store schedules-api)
                  schedule)
    schedule))

(defn delete! [schedule-api schedule-id]
  (isched/delete! (:store schedule-api) schedule-id)
  nil)

(defn relevant-notes [schedules-api timestamp]
  (let [note-ids (select-note-ids (:store schedules-api) timestamp)]
    (notes/get-notes-by-ids (:notes-api schedules-api) note-ids)))
