(ns brainard.infra.stores.notes
  (:require
    [brainard.api.notes.proto :as notes.proto]
    [brainard.infra.datomic :as datomic]
    [clj-uuid :as uuid])
  (:import
    (java.util Date)))

(defn- save! [{:keys [datomic-conn]} note]
  (datomic/transact! datomic-conn
                     {:tx-data [(assoc note
                                       :notes/id (uuid/v4)
                                       :notes/timestamp (Date.))]}))

(defn- get-contexts [{:keys [datomic-conn]}]
  (->> (datomic/query datomic-conn
                      '[:find ?context
                        :where [_ :notes/context ?context]])
       (map first)
       set))

(defn- get-tags [{:keys [datomic-conn]}]
  (->> (datomic/query datomic-conn
                      '[:find ?tag
                        :where [_ :notes/tags ?tag]])
       (map first)
       set))

(defn- notes-query [{:keys [context tag]}]
  (cond-> '[:find (pull ?e [:notes/id
                            :notes/context
                            :notes/body
                            :notes/tags
                            :notes/timestamp])
            :in $]

    context
    (conj '?context)

    tag
    (conj '?tag)

    :always
    (conj :where)

    (and (not context) (not tag))
    (conj '[?e :notes/body])

    context
    (conj '[?e :notes/context ?context])

    tag
    (conj '[?e :notes/tags ?tag])))

(defn- get-notes [{:keys [datomic-conn]} {:keys [context tag] :as params}]
  (let [query (notes-query params)]
    (->> [context tag]
         (remove nil?)
         (apply datomic/query datomic-conn query)
         (map #(update (first %) :notes/tags set))
         (sort-by :notes/timestamp))))

(defn create-store [this]
  (with-meta this
             {`notes.proto/save!        #'save!
              `notes.proto/get-contexts #'get-contexts
              `notes.proto/get-notes    #'get-notes
              `notes.proto/get-tags     #'get-tags}))
