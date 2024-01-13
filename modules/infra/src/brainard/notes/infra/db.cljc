(ns brainard.notes.infra.db
  (:require
    [brainard.notes.api.interfaces :as inotes]
    [brainard.infra.db.datascript :as ds]))

(def ^:private select
  '[:find (pull ?e [:notes/id
                    :notes/context
                    :notes/body
                    :notes/tags
                    :notes/timestamp])
    :in $])

(defn ^:private save! [{:keys [datascript-conn]} note]
  (let [{note-id :notes/id retract-tags :notes/tags!remove} note]
    (ds/transact! datascript-conn
                  (into [(dissoc note :notes/tags!remove)]
                        (map (partial conj [:db/retract [:notes/id note-id] :notes/tags]))
                        retract-tags))))

(defn ^:private get-contexts [{:keys [datascript-conn]}]
  (->> (ds/query datascript-conn
                 '[:find ?context
                   :where [_ :notes/context ?context]])
       (map first)))

(defn ^:private get-tags [{:keys [datascript-conn]}]
  (->> (ds/query datascript-conn
                 '[:find ?tag
                   :where [_ :notes/tags ?tag]])
       (map first)))

(defn ^:private notes-query [{:notes/keys [context tags]}]
  (cond-> (conj select :where)
    (some? context)
    (conj ['?e :notes/context context])

    (seq tags)
    (into (map (partial conj '[?e :notes/tags])) tags)))

(defn ^:private get-notes [{:keys [datascript-conn]} params]
  (let [query (notes-query params)]
    (->> (ds/query datascript-conn query)
         (map first))))

(defn ^:private get-notes-by-ids [{:keys [datascript-conn]} note-ids]
  (->> (ds/query datascript-conn
                 (into select
                       '[[?id ...]
                         :where [?e :notes/id ?id]])
                 note-ids)
       (map first)))

(defn ^:private get-note [{:keys [datascript-conn]} note-id]
  (-> (ds/query datascript-conn
                (into select '[?note-id
                               :where [?e :notes/id ?note-id]])
                note-id)
      ffirst))

(defn create-store
  "Creates a notes store which implements [[inotes/INotesStore]]."
  [this]
  (with-meta this
             {`inotes/save!            #'save!
              `inotes/get-contexts     #'get-contexts
              `inotes/get-tags         #'get-tags
              `inotes/get-notes        #'get-notes
              `inotes/get-notes-by-ids #'get-notes-by-ids
              `inotes/get-note         #'get-note}))
