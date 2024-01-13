(ns brainard.common.api.notes.interfaces)

(defprotocol INotesStore
  "Saves/retrieves notes from a store."
  :extend-via-metadata true
  (save! [this note]
    "Saves a note to the store.")
  (get-tags [this]
    "Returns all tags from the store.")
  (get-contexts [this]
    "Returns all contexts from the store.")
  (get-note [this note-id]
    "Returns a note with the primary key or nil.")
  (get-notes-by-ids [this note-ids]
    "Returns a sequence of notes for the provided ids.")
  (get-notes [this filters]
    "Returns a sequence of notes that match the filters."))
