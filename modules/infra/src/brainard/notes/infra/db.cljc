(ns brainard.notes.infra.db
  (:require
    [brainard.api.storage.core :as-alias storage]
    [brainard.infra.db.store :as ds]
    [brainard.notes.api.core :as api.notes]
    [brainard.api.storage.interfaces :as istorage]))

(def ^:private select
  '[:find (pull ?e [*])
    :in $])

(defn ^:private notes-query [{:notes/keys [context ids pinned? tags]}]
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
    (into (map (partial conj '[?e :notes/tags])) tags)

    pinned?
    (conj ['?e :notes/pinned? true])))

(defn update-note [db {note-id :notes/id retract-tags :notes/tags!remove :as note}]
  (when (ds/query db (istorage/->input {::storage/type ::api.notes/get-note
                                        :notes/id      note-id}))
    (into [(select-keys note #{:notes/id
                               :notes/context
                               :notes/body
                               :notes/tags
                               :notes/pinned?})]
          (map (partial conj [:db/retract [:notes/id note-id] :notes/tags]))
          retract-tags)))

(defmethod istorage/->input ::api.notes/create!
  [note]
  [(select-keys note #{:notes/id
                       :notes/context
                       :notes/body
                       :notes/tags
                       :notes/pinned?
                       :notes/timestamp})])

(defmethod istorage/->input ::api.notes/update!
  [note]
  [#?(:clj  `[update-note ~note]
      :cljs [:db.fn/call update-note note])])

(defmethod istorage/->input ::api.notes/delete!
  [{note-id :notes/id}]
  [[:db/retractEntity [:notes/id note-id]]])

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

(defmethod istorage/->input ::api.notes/get-notes
  [params]
  {:query (notes-query params)
   :args  (some-> (:notes/ids params) vector)
   :xform (map first)})

(defmethod istorage/->input ::api.notes/get-note
  [{:notes/keys [id]}]
  {:query (into select '[?note-id
                         :where [?e :notes/id ?note-id]])
   :args  [id]
   :only? true
   :xform (map first)})
