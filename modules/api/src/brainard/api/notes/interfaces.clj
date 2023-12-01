(ns brainard.api.notes.interfaces)

(defprotocol INotesStore
  :extend-via-metadata true
  (save! [this note])
  (get-tags [this])
  (get-contexts [this])
  (get-note [this note-id])
  (get-notes [this params]))
