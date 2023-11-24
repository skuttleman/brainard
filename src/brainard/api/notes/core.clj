(ns brainard.api.notes.core
  (:require
    [brainard.api.notes.proto :as notes.proto]
    [clj-uuid :as uuid])
  (:import
    (java.util Date)))

(defn clean-note [note note-id]
  (-> note
      (select-keys #{:notes/body :notes/context :notes/tags :notes.retract/tags})
      (update :notes/tags set)
      (assoc :notes/id note-id)))

(defn update! [{:keys [store]} note-id note]
  (let [note (clean-note note note-id)]
    (notes.proto/save! store note)
    (notes.proto/get-note store note-id)))

(defn create! [{:keys [store]} note]
  (let [note (-> note
                 (clean-note (uuid/squuid))
                 (assoc :notes/timestamp (Date.)))]
    (notes.proto/save! store note)
    note))

(defn get-tags [{:keys [store]}]
  (notes.proto/get-tags store))

(defn get-contexts [{:keys [store]}]
  (notes.proto/get-contexts store))

(defn get-notes [{:keys [store]} params]
  (sort-by :notes/timestamp (notes.proto/get-notes store params)))
