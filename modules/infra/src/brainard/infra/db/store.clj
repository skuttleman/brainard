(ns brainard.infra.db.store
  "This uses a datomic local client"
  (:require
    [datomic.client.api :as d]
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]))

(defn transact!
  "Issue a transaction to datomic"
  [conn tx]
  (d/transact (first conn) {:tx-data tx}))

(defn query
  "Make a query against a datomic db"
  [db {:keys [args only? query history? post ref? xform]}]
  (cond-> (->> args
               (apply d/q query (cond-> db history? d/history))
               (walk/postwalk (fn [x]
                                (cond-> x
                                  (map? x) (cond->
                                             ref? (set/rename-keys {:db/id :brainard/ref})
                                             (not ref?) (dissoc :db/id)))))
               (sequence (or xform identity)))
    only? first
    post post))

(defn connect! [{:keys [storage-dir db-name schema]}]
  (let [client (d/client {:server-type :datomic-local
                          :storage-dir (or storage-dir
                                           (format "%s/.datomic-storage"
                                                   (System/getProperty "user.dir")))
                          :system      "main"})]
    (d/create-database client {:db-name db-name})
    (with-meta [(doto (d/connect client {:db-name db-name})
                  (d/transact {:tx-data schema}))
                client]
               {::db-name db-name})))

(deftype DSStore [conn]
  istorage/IRead
  (read [_ params]
    (query (d/db (first conn)) params))

  istorage/IWrite
  (write! [_ tx]
    (transact! conn tx)))
