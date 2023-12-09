(ns brainard.api.notes.core
  (:require
    [brainard.api.notes.interfaces :as inotes]
    [brainard.common.utils.uuids :as uuids])
  (:import
    (java.util Date)))

(defn ^:private tag-set [note]
  (update note :notes/tags set))

(defn ^:private clean-note [note note-id]
  (-> note
      (select-keys #{:notes/body :notes/context :notes/tags :notes/tags!remove})
      tag-set
      (assoc :notes/id note-id)))

(defn update!
  "Updates a note in the store and returns the updated note."
  [notes-api note-id note]
  (when (inotes/get-note (:store notes-api) note-id)
    (let [note (clean-note note note-id)]
      (inotes/save! (:store notes-api) note)
      (tag-set (inotes/get-note (:store notes-api) note-id)))))

(defn create!
  "Creates a note in the store and returns the created note."
  [notes-api note]
  (let [note (-> note
                 (clean-note (uuids/random))
                 (assoc :notes/timestamp (Date.)))]
    (inotes/save! (:store notes-api) note)
    note))

(defn get-tags
  "Retrieve all tags."
  [notes-api]
  (set (inotes/get-tags (:store notes-api))))

(defn get-contexts
  "Retrieve all contexts."
  [notes-api]
  (set (inotes/get-contexts (:store notes-api))))

(defn get-notes
  "Selects notes that match a query. Query cannot be empty.
  (get-notes notes-api {:notes/context \"some context\" :notes/tags #{:tag-1 :tag-2}})"
  [notes-api params]
  (->> (inotes/get-notes (:store notes-api) params)
       (map tag-set)
       (sort-by :notes/timestamp)))

(defn get-note
  "Find note by primary key."
  [notes-api note-id]
  (some-> (inotes/get-note (:store notes-api) note-id) tag-set))
