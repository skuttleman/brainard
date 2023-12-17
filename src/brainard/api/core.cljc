(ns brainard.api.core
  (:require
    [brainard.api.notes.core :as api.notes]
    [brainard.api.schedules.core :as api.sched]))

(defn create-note! [apis note]
  (api.notes/create! (:notes apis) note))

(defn update-note! [apis note-id note]
  (api.notes/update! (:notes apis) note-id note))

(defn get-notes [apis params]
  (api.notes/get-notes (:notes apis) params))

(defn get-note [apis note-id]
  (when-let [note (api.notes/get-note (:notes apis) note-id)]
    (let [schedules (api.sched/get-by-note-id (:schedules apis) note-id)]
      (assoc note :notes/schedules schedules))))

(defn get-tags [apis]
  (api.notes/get-tags (:notes apis)))

(defn get-contexts [apis]
  (api.notes/get-contexts (:notes apis)))

(defn create-schedule! [apis schedule]
  (api.sched/create! (:schedules apis) schedule))

(defn delete-schedule! [apis schedule-id]
  (api.sched/delete! (:schedules apis) schedule-id))

(defn relevant-notes [apis timestamp]
  (->> (api.sched/relevant-schedules (:schedules apis) timestamp)
       (map :schedules/note-id)
       (api.notes/get-notes-by-ids (:notes apis))))
