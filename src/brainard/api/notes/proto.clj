(ns brainard.api.notes.proto)

(defprotocol INotesStore
  :extend-via-metadata true
  (save! [this note])
  (get-tags [this])
  (get-contexts [this])
  (get-notes [this params]))
