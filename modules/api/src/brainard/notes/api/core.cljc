(ns brainard.notes.api.core
  (:require
    [brainard.api.utils.uuids :as uuids]
    [brainard.api.storage.core :as storage])
  #?(:clj
     (:import
       (java.util Date))))

(defn ^:private tag-set [note]
  (update note :notes/tags set))

(defn ^:private clean-note [note note-id]
  (-> note
      (select-keys #{:notes/body :notes/context :notes/tags :notes/tags!remove :notes/pinned?})
      tag-set
      (assoc :notes/id note-id)))

(defn update!
  "Updates a note in the store and returns the updated note."
  [notes-api note-id note]
  (when (storage/query (:store notes-api)
                       {::storage/type ::get-note
                        :notes/id      note-id})
    (let [note (clean-note note note-id)]
      (storage/execute! (:store notes-api)
                        (assoc note
                               :notes/id note-id
                               ::storage/type ::save!))
      (tag-set (storage/query (:store notes-api)
                              {::storage/type ::get-note
                               :notes/id      note-id})))))

(defn create!
  "Creates a note in the store and returns the created note."
  [notes-api note]
  (let [note (-> note
                 (clean-note (uuids/random))
                 (assoc :notes/timestamp #?(:cljs (js/Date.) :default (Date.))))]
    (storage/execute! (:store notes-api) (assoc note ::storage/type ::save!))
    note))

(defn get-tags
  "Retrieve all tags."
  [notes-api]
  (set (storage/query (:store notes-api) {::storage/type ::get-tags})))

(defn get-contexts
  "Retrieve all contexts."
  [notes-api]
  (set (storage/query (:store notes-api) {::storage/type ::get-contexts})))

(defn get-notes
  "Selects notes that match a query. Query cannot be empty.
  (get-notes notes-api {:notes/context \"some context\" :notes/tags #{:tag-1 :tag-2}})"
  [notes-api params]
  (->> (assoc params ::storage/type ::get-notes)
       (storage/query (:store notes-api))
       (map tag-set)
       (sort-by :notes/timestamp)))

(defn get-note
  "Find note by primary key."
  [notes-api note-id]
  (some-> (storage/query (:store notes-api) {::storage/type ::get-note
                                             :notes/id      note-id})
          tag-set))
