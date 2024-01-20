(ns brainard.notes.api.interfaces)

(defprotocol IWriteNotes
  "Saves notes to a store."
  :extend-via-metadata true
  (save! [this note]
    "Saves a note to the store."))

(defprotocol IReadNotes
  "Retrieves notes from a store."
  :extend-via-metadata true
  (get-note [this note-id]
    "Returns a note with the primary key or nil.")
  (get-notes [this filters]
    "Returns a sequence of notes that match the filters."))

(defprotocol IReadAutocompleteAttributes
  "Retrieves indexed attributes"
  :extend-via-metadata true
  (get-tags [this]
    "Returns all tags from the store.")
  (get-contexts [this]
    "Returns all contexts from the store."))
