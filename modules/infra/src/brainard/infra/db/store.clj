(ns brainard.infra.db.store
  "This uses a datomic local client"
  (:require
    [brainard.infra.utils.edn :as edn]
    [brainard.api.utils.logger :as log]
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datomic.client.api :as d]))

(defn file-logger [db-name]
  (let [dir (format "%s/.datomic-storage" (System/getProperty "user.dir"))]
    {::db-name  db-name
     ::storage-dir dir}))

(defn ^:private transact! [conn tx]
  (log/with-duration [{:keys [duration]} (d/transact (first conn) {:tx-data tx})]
    (log/debug "db transaction:" (str "[" duration "ms]"))))

(defn ^:private query [conn query & args]
  (apply d/q query (d/db (first conn)) args))

(defn ^:private do-query [conn {:keys [args only? xform ref?] :as params}]
  (cond-> (->> (apply query conn (:query params) args)
               (walk/postwalk (fn [x]
                                (cond-> x
                                  (map? x) (cond->
                                             ref? (set/rename-keys {:db/id :brainard/ref})
                                             (not ref?) (dissoc :db/id)))))
               (sequence (or xform identity)))
    only? first))

(defn connect!
  "Connects a client to a database."
  [{::keys [db-name storage-dir] :as logger}]
  (let [client (doto (d/client {:server-type :datomic-local
                                :storage-dir storage-dir
                                :system "dev"})
                 (d/create-database {:db-name db-name}))
        conn (d/connect client {:db-name db-name})]
    (d/transact conn {:tx-data (edn/resource "schema.edn")})
    (with-meta [conn client] logger)))

(deftype DSStore [conn]
  istorage/IRead
  (read [_ params]
    (do-query conn params))

  istorage/IWrite
  (write! [_ tx]
    (transact! conn tx)))
