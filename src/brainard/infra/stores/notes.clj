(ns brainard.infra.stores.notes
  (:require
    [brainard.api.notes.proto :as notes.proto]
    [brainard.infra.datomic :as datomic]))

(def ^:private ^:const select
  '[:find (pull ?e [:notes/id
                    :notes/context
                    :notes/body
                    :notes/tags
                    :notes/timestamp])])

(defn ^:private save! [{:keys [datomic-conn]} note]
  (let [{note-id :notes/id retract-tags :notes.retract/tags} note]
    (datomic/transact! datomic-conn
                       {:tx-data (into [(dissoc note :notes.retract/tags)]
                                       (map (partial conj [:db/retract [:notes/id note-id] :notes/tags]))
                                       retract-tags)})))

(defn ^:private get-contexts [{:keys [datomic-conn]}]
  (->> (datomic/query datomic-conn
                      '[:find ?context
                        :where [_ :notes/context ?context]])
       (map first)
       set))

(defn ^:private get-tags [{:keys [datomic-conn]}]
  (->> (datomic/query datomic-conn
                      '[:find ?tag
                        :where [_ :notes/tags ?tag]])
       (map first)
       set))

(defn ^:private notes-query [{:notes/keys [context tags]}]
  (cond-> (conj select :where)
    (some? context)
    (conj ['?e :notes/context context])

    (seq tags)
    (into (map (partial conj '[?e :notes/tags])) tags)))

(defn ^:private get-notes [{:keys [datomic-conn]} params]
  (let [query (notes-query params)]
    (->> (datomic/query datomic-conn query)
         (map #(update (first %) :notes/tags set)))))

(defn ^:private get-note [{:keys [datomic-conn]} note-id]
  (-> (datomic/query datomic-conn
                     (into select '[:in $ ?note-id
                                    :where [?e :notes/id ?note-id]])
                     note-id)
      ffirst
      (update :notes/tags set)))

(defn create-store [this]
  (with-meta this
             {`notes.proto/save!        #'save!
              `notes.proto/get-contexts #'get-contexts
              `notes.proto/get-tags     #'get-tags
              `notes.proto/get-notes    #'get-notes
              `notes.proto/get-note     #'get-note}))
