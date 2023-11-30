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
      (select-keys #{:notes/body :notes/context :notes/tags :notes.retract/tags})
      tag-set
      (assoc :notes/id note-id)))

(defn update! [{:keys [store]} note-id note]
  (when (inotes/get-note store note-id)
    (let [note (clean-note note note-id)]
      (inotes/save! store note)
      (tag-set (inotes/get-note store note-id)))))

(defn create! [{:keys [store]} note]
  (let [note (-> note
                 (clean-note (uuids/random))
                 (assoc :notes/timestamp (Date.)))]
    (inotes/save! store note)
    note))

(defn get-tags [{:keys [store]}]
  (set (inotes/get-tags store)))

(defn get-contexts [{:keys [store]}]
  (set (inotes/get-contexts store)))

(defn get-notes [{:keys [store]} params]
  (->> (inotes/get-notes store params)
       (map tag-set)
       (sort-by :notes/timestamp)))

(defn get-note [{:keys [store]} note-id]
  (tag-set (inotes/get-note store note-id)))
