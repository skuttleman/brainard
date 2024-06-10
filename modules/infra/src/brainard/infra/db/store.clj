(ns brainard.infra.db.store
  "This uses a datomic local client"
  (:require
    [clojure.java.io :as io]
    [brainard.api.utils.logger :as log]
    [brainard.infra.utils.edn :as edn]
    [brainard.resources.db :as db]
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datascript.core :as d]))

(defn file-logger [db-name]
  (let [file-name (format "resources/.ds.%s.log" db-name)]
    {::db-name  db-name
     ::writer   (io/writer (io/file file-name) :append true)
     ::lock     (Object.)
     ::log-file file-name}))

(defn ^:private write! [{::keys [lock writer]} data]
  (locking lock
    (.write writer (pr-str data))
    (.append writer \newline)
    (.flush writer)))

(defn ^:private load-log! [conn]
  (let [logger (meta conn)
        conn (first conn)]
    (let [{::keys [lock log-file]} logger]
      (locking lock
        (with-open [reader (io/reader (io/file log-file))]
          (doseq [line (line-seq reader)]
            (d/transact conn (edn/read-string line))))))))

(defn ^:private transact! [conn tx]
  (log/with-duration [{:keys [duration]} (doto conn
                                           (-> first (d/transact! tx))
                                           (-> meta (write! tx)))]
    (log/debug "db transaction:" (str "[" duration "ms]"))))

(defn ^:private query [conn query & args]
  (apply d/q query (d/db (first conn)) args))

(defn ^:private init!
  "Initializes the in-memory db with previously transacted data."
  [conn]
  (log/with-duration [{:keys [duration]} (doto conn load-log!)]
    (log/info "db initialization:" (str "[" duration "ms]"))))

(defn connect!
  "Connects a client to a database."
  [logger]
  (doto (with-meta [(d/create-conn db/schema)] logger)
    init!))

(defn close!
  "Closes a client's connection to a database."
  [conn]
  (let [{::keys [lock writer]} (meta conn)]
    (locking lock
      (.close writer))))

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
