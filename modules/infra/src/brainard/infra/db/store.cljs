(ns brainard.infra.db.store
  "This uses an in-memory datascript client which writes transactions to localStorage"
  (:require-macros brainard.infra.db.store)
  (:require
    [brainard.resources.db :as db]
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datascript.core :as d]))

(defn local-storage-logger [db-name]
  {::db-name db-name})

(defn ^:private write! [{::keys [db-name]} conn]
  (.setItem js/localStorage db-name (-> (d/db (first conn))
                                        d/serializable
                                        js/JSON.stringify)))

(defn ^:private load-log! [conn]
  (let [logger (meta conn)
        conn (first conn)]
    (let [{::keys [db-name]} logger]
      (when-let [db (some-> (.getItem js/localStorage db-name)
                            js/JSON.parse
                            d/from-serializable)]
        (d/reset-conn! conn db)))))

(defn ^:private transact! [conn tx]
  (doto conn
    (-> first (d/transact! tx))
    (-> meta (write! conn))))

(defn ^:private query [conn query & args]
  (apply d/q query (d/db (first conn)) args))

(defn ^:private init!
  "Initializes the in-memory datascript store with previously transacted data."
  [conn]
  (doto conn load-log!))

(defn connect!
  "Connects a client to a database."
  [logger]
  (doto (with-meta [(d/create-conn db/schema)] logger)
    init!))

(defn ^:private do-query [conn {:keys [args only? xform ref?] :as params}]
  (cond-> (->> (apply query conn (:query params) args)
               (walk/postwalk (fn [x]
                                (cond-> x
                                  (map? x) (cond->
                                             ref? (set/rename-keys {:db/id :brainard/ref})
                                             (not ref?) (dissoc :db/id)))))
               (sequence (or xform identity)))
    only? first))

(deftype DSStore [conn]
  istorage/IRead
  (read [_ params]
    (do-query conn params))

  istorage/IWrite
  (write! [_ tx]
    (transact! conn tx)))
