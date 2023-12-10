(ns brainard.infra.stores.notes
  (:require
    [brainard.api.notes.interfaces :as inotes]
    [brainard.infra.services.datomic :as datomic]))

(def ^:private ^:const select
  '[:find (pull ?e [:notes/id
                    :notes/context
                    :notes/body
                    :notes/tags
                    :notes/timestamp])
    :in $])

(defn ^:private save! [{:keys [datomic-conn]} note]
  (let [{note-id :notes/id retract-tags :notes/tags!remove} note]
    (datomic/transact! datomic-conn
                       {:tx-data (into [(dissoc note :notes/tags!remove)]
                                       (map (partial conj [:db/retract [:notes/id note-id] :notes/tags]))
                                       retract-tags)})))

(defn ^:private get-contexts [{:keys [datomic-conn]}]
  (->> (datomic/query datomic-conn
                      '[:find ?context
                        :where [_ :notes/context ?context]])
       (map first)))

(defn ^:private get-tags [{:keys [datomic-conn]}]
  (->> (datomic/query datomic-conn
                      '[:find ?tag
                        :where [_ :notes/tags ?tag]])
       (map first)))

(defn ^:private notes-query [{:notes/keys [context tags]}]
  (cond-> (conj select :where)
    (some? context)
    (conj ['?e :notes/context context])

    (seq tags)
    (into (map (partial conj '[?e :notes/tags])) tags)))

(defn ^:private get-notes [{:keys [datomic-conn]} params]
  (let [query (notes-query params)]
    (->> (datomic/query datomic-conn query)
         (map first))))

(defn ^:private get-notes-by-ids [{:keys [datomic-conn]} note-ids]
  (->> (datomic/query datomic-conn
                      (into select
                            '[[?id ...]
                             :where [?e :notes/id ?id]])
                      note-ids)
       (map first)))

(defn ^:private get-note [{:keys [datomic-conn]} note-id]
  (-> (datomic/query datomic-conn
                     (into select '[?note-id
                                    :where [?e :notes/id ?note-id]])
                     note-id)
      ffirst))

(defn create-store
  "Creates a notes store which implements [[inotes/INotesStore]]."
  [this]
  (with-meta this
             {`inotes/save!        #'save!
              `inotes/get-contexts #'get-contexts
              `inotes/get-tags     #'get-tags
              `inotes/get-notes    #'get-notes
              `inotes/get-notes-by-ids #'get-notes-by-ids
              `inotes/get-note     #'get-note}))