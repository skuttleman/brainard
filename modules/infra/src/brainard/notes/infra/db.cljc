(ns brainard.notes.infra.db
  (:require
    [brainard.notes.api.core :as api.notes]
    [brainard.api.storage.interfaces :as istorage]))

(def ^:private select
  '[:find (pull ?e [*])
    :in $])

(defmethod istorage/->input ::api.notes/save!
  [note]
  (let [{note-id :notes/id retract-tags :notes/tags!remove} note]
    (into [(select-keys note #{:notes/id
                               :notes/context
                               :notes/body
                               :notes/tags
                               :notes/timestamp})]
          (map (partial conj [:db/retract [:notes/id note-id] :notes/tags]))
          retract-tags)))

(defmethod istorage/->input ::api.notes/get-contexts
  [_]
  {:query '[:find ?context
            :where [_ :notes/context ?context]]
   :xform (map first)})

(defmethod istorage/->input ::api.notes/get-tags
  [_]
  {:query '[:find ?tag
            :where [_ :notes/tags ?tag]]
   :xform (map first)})

(defn ^:private notes-query [{:notes/keys [context tags ids]}]
  (cond-> select
    (seq ids)
    (conj '[?id ...])

    :always
    (conj :where)

    (seq ids)
    (conj '[?e :notes/id ?id])

    (some? context)
    (conj ['?e :notes/context context])

    (seq tags)
    (into (map (partial conj '[?e :notes/tags])) tags)))

(defmethod istorage/->input ::api.notes/get-notes
  [params]
  {:query (notes-query params)
   :args  (:notes/ids params)
   :xform (map first)})

(defmethod istorage/->input ::api.notes/get-note
  [{:notes/keys [id]}]
  {:query (into select '[?note-id
                         :where [?e :notes/id ?note-id]])
   :args  [id]
   :only? true
   :xform (map first)})
