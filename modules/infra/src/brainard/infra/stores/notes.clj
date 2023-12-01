(ns brainard.infra.stores.notes
  (:require
    [brainard.api.notes.interfaces :as inotes]
    [brainard.infra.services.datomic :as datomic]))

(def ^:private ^:const select
  '[:find (pull ?e [:notes/id
                    :notes/context
                    :notes/body
                    :notes/tags
                    :notes/timestamp])])

(defn ^:private save! [{:keys [datomic-conn]} note]
  (let [{note-id :notes/id retract-tags :notes/tags#removed} note]
    (datomic/transact! datomic-conn
                       {:tx-data (into [(dissoc note :notes/tags#removed)]
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

(defn ^:private get-note [{:keys [datomic-conn]} note-id]
  (-> (datomic/query datomic-conn
                     (into select '[:in $ ?note-id
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
              `inotes/get-note     #'get-note}))
