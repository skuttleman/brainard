(ns brainard.api.schedules.interfaces)

(defprotocol ISchedulesStore
  "Saves/retrieves schedules for a note from a store"
  :extend-via-metadata true
  (save! [this schedule]
    "Saves a schedule to the store.")
  (delete! [this schedule-id]
    "Deletes a schedule by id.")
  (get-schedules [this filters]
    "Returns schedules that match filters"))
