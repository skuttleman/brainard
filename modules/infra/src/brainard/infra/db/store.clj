(ns brainard.infra.db.store
  "This uses a datomic local client"
  (:require
    [brainard.api.utils.logger :as log]
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datomic.client.api :as d]))

(defn ^:private transact! [conn tx]
  (log/with-duration [{:keys [duration]} (d/transact (first conn) {:tx-data tx})]
    (log/debug "db transaction:" (str "[" duration "ms]"))))

(defn ^:private do-query [conn {:keys [args only? xform ref?] :as params}]
  (cond-> (->> (apply d/q (:query params) (d/db (first conn)) args)
               (walk/postwalk (fn [x]
                                (cond-> x
                                  (map? x) (cond->
                                             ref? (set/rename-keys {:db/id :brainard/ref})
                                             (not ref?) (dissoc :db/id)))))
               (sequence (or xform identity)))
    only? first))

(defn ->conn [client db-name schema]
  (d/create-database client {:db-name db-name})
  [(doto (d/connect client {:db-name db-name})
     (d/transact {:tx-data schema}))])

(defn ->client [storage-dir]
  (d/client {:server-type :datomic-local
             :storage-dir (or storage-dir
                              (format "%s/.datomic-storage" (System/getProperty "user.dir")))
             :system      "main"}))

(deftype DSStore [conn]
  istorage/IRead
  (read [_ params]
    (do-query conn params))

  istorage/IWrite
  (write! [_ tx]
    (transact! conn tx)))
