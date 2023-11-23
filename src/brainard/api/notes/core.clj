(ns brainard.api.notes.core
  (:require
    [brainard.api.notes.proto :as notes.proto]
    [clj-uuid :as uuid]))

(defn update-note! [{:keys [store]} note-id note]
  (notes.proto/save! store (-> note
                               (update :notes/tags set)
                               (select-keys #{:notes/body :notes/context :notes/tags})
                               (assoc :notes/id note-id)))
  note-id)

(defn take-note! [this note]
  (update-note! this (uuid/v4) note))

(defn get-tags [{:keys [store]}]
  (notes.proto/get-tags store))

(defn get-contexts [{:keys [store]}]
  (notes.proto/get-contexts store))

(defn get-notes [{:keys [store]} query]
  (notes.proto/get-notes store query))
