(ns brainard.schedules.api.interfaces)

(defprotocol IWriteSchedules
  "Retrieves schedules for a note from a store."
  :extend-via-metadata true
  (save! [this schedule]
    "Saves a schedule to the store.")
  (delete! [this schedule-id]
    "Deletes a schedule by id."))

(defprotocol IReadSchedules
  "Saves schedules for a note to a store."
  :extend-via-metadata true
  (get-schedules [this filters]
    "Returns schedules that match filters")
  (get-by-note-id [this note-id]
    "Returns schedules for a specific note"))
