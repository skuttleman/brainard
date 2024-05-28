(ns brainard.resources.db)

(def schema
  {;; notes
   :notes/id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "The external id of the note"}

   :notes/context
   {:db/cardinality :db.cardinality/one
    :db/doc         "The relevant meeting/context"}

   :notes/body
   {:db/cardinality :db.cardinality/one
    :db/doc         "The body of the note"}

   :notes/tags
   {:db/cardinality :db.cardinality/many
    :db/doc         "Associated tags"}

   :notes/pinned?
   {:db/cardinality :db.cardinality/one
    :db/doc         "Is the note pinned to the workspace"}

   :notes/timestamp
   {:db/cardinality :db.cardinality/one
    :db/doc         "The time the note was recorded"}

   ;; schedules
   :schedules/id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "The external id of the schedule"}

   :schedules/note-id
   {:db/cardinality :db.cardinality/one
    :db/doc         "The id of the note to which the schedule applies"}

   :schedules/before-timestamp
   {:db/cardinality :db.cardinality/one
    :db/doc         "A maximum UTC timestamp before which the schedule applies"}

   :schedules/after-timestamp
   {:db/cardinality :db.cardinality/one
    :db/doc         "A minimum UTC timestamp after which the schedule applies"}

   :schedules/month
   {:db/cardinality :db.cardinality/one
    :db/doc         "The month when the schedule applies"}

   :schedules/day
   {:db/cardinality :db.cardinality/one
    :db/doc         "The day-of-the-month when the schedule applies"}

   :schedules/weekday
   {:db/cardinality :db.cardinality/one
    :db/doc         "The weekday the schedule applies"}

   :schedules/week-index
   {:db/cardinality :db.cardinality/one
    :db/doc         "The week index (i.e. first week = 0, second week = 1, etc.) when the schedule applies"}})
