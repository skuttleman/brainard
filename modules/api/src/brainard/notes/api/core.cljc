(ns brainard.notes.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.fns :as fns]
    [brainard.api.utils.uuids :as uuids]))

(defn ^:private coll-set [note]
  (some-> note
          (update :notes/tags set)
          (update :notes/attachments set)
          (update :notes/todos set)))

(defn ^:private clean-note [note note-id]
  (-> note
      coll-set
      (assoc :notes/id note-id)))

(defn update!
  "Updates a note in the store and returns the updated note."
  [notes-api note-id note]
  (when (storage/query (:store notes-api)
                       {::storage/type ::get-note
                        :notes/id      note-id})
    (storage/execute! (:store notes-api)
                      (assoc note
                             ::storage/type ::update!
                             :notes/id note-id))
    (coll-set (storage/query (:store notes-api)
                             {::storage/type ::get-note
                             :notes/id      note-id}))))

(defn delete!
  "Deletes a note by id"
  [notes-api note-id]
  (storage/execute! (:store notes-api)
                    {::storage/type ::delete!
                     :notes/id      note-id})
  nil)

(defn create!
  "Creates a note in the store and returns the created note."
  [notes-api note]
  (let [note-id (uuids/random)
        note (-> note
                 (update :notes/todos fns/smap #(assoc % :todos/id (uuids/random)))
                 (clean-note note-id))]
    (storage/execute! (:store notes-api) (assoc note ::storage/type ::create!))
    (coll-set (storage/query (:store notes-api)
                             {::storage/type ::get-note
                             :notes/id      note-id}))))

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
       (map coll-set)
       (sort-by :notes/timestamp)))

(defn get-note
  "Find note by primary key."
  [notes-api note-id]
  (coll-set (storage/query (:store notes-api) {::storage/type ::get-note
                                              :notes/id       note-id})))

(defn get-note-history [notes-api note-id]
  #?(:clj (storage/query (:store notes-api) {::storage/type ::get-note-history
                                             :notes/id      note-id})))
