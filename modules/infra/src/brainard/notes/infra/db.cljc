(ns brainard.notes.infra.db
  (:require
    [brainard.infra.db.datascript :as ds]
    [brainard.notes.api.interfaces :as inotes]))

(def ^:private select
  '[:find (pull ?e [:notes/id
                    :notes/context
                    :notes/body
                    :notes/tags
                    :notes/timestamp])
    :in $])

(defn ^:private save! [{:keys [ds-client]} note]
  (let [{note-id :notes/id retract-tags :notes/tags!remove} note]
    (ds/transact! ds-client
                  (into [(dissoc note :notes/tags!remove)]
                        (map (partial conj [:db/retract [:notes/id note-id] :notes/tags]))
                        retract-tags))))

(defn ^:private get-contexts [{:keys [ds-client]}]
  (->> (ds/query ds-client
                 '[:find ?context
                   :where [_ :notes/context ?context]])
       (map first)))

(defn ^:private get-tags [{:keys [ds-client]}]
  (->> (ds/query ds-client
                 '[:find ?tag
                   :where [_ :notes/tags ?tag]])
       (map first)))

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

(defn ^:private get-notes [{:keys [ds-client]} {:notes/keys [ids] :as params}]
  (let [query (notes-query params)
        result (if (seq ids)
                 (ds/query ds-client query ids)
                 (ds/query ds-client query))]
    (map first result)))

(defn ^:private get-note [{:keys [ds-client]} note-id]
  (-> (ds/query ds-client
                (into select '[?note-id
                               :where [?e :notes/id ?note-id]])
                note-id)
      ffirst))

(defn create-store
  "Creates a notes store which implements the interfaces in [[inotes]]."
  [this]
  (with-meta this
             {`inotes/save!            #'save!
              `inotes/get-contexts     #'get-contexts
              `inotes/get-tags         #'get-tags
              `inotes/get-notes        #'get-notes
              `inotes/get-note         #'get-note}))
