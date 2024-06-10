(ns brainard.infra.db.datascript
  "This uses an in-memory datascript client which writes transactions to a file to survive refreshes."
  #?(:cljs (:require-macros brainard.infra.db.datascript))
  (:require
    #?(:clj [clojure.java.io :as io])
    [brainard.api.utils.logger :as log]
    [brainard.infra.utils.edn :as edn]
    [brainard.resources.db :as db]
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datascript.core :as d]))

#?(:clj
   (defn file-logger [db-name]
     (let [file-name (format "resources/.ds.%s.log" db-name)]
       {::db-name  db-name
        ::writer   (io/writer (io/file file-name) :append true)
        ::lock     (Object.)
        ::log-file file-name}))
   :cljs
   (defn local-storage-logger [db-name]
     {::db-name db-name}))

(defn ^:private write! [{::keys [db-name lock writer]} conn data]
  #?(:clj  (locking lock
             (.write writer (pr-str data))
             (.append writer \newline)
             (.flush writer))
     :cljs (.setItem js/localStorage db-name (pr-str (d/serializable (d/db (first conn)))))))

(defn ^:private load-log! [conn]
  (let [logger (meta conn)
        conn (first conn)]
    #?(:clj  (let [{::keys [lock log-file]} logger]
               (locking lock
                 (with-open [reader (io/reader (io/file log-file))]
                   (doseq [line (line-seq reader)]
                     (d/transact conn (edn/read-string line))))))
       :cljs (let [{::keys [db-name]} logger]
               (when-let [db (some-> (.getItem js/localStorage db-name)
                                     edn/read-string
                                     d/from-serializable)]
                 (d/reset-conn! conn db))))))

(defn ^:private transact! [conn tx]
  (log/with-duration [{:keys [duration]} (doto conn
                                           (-> first (d/transact! tx))
                                           (-> meta (write! conn tx)))]
    #?(:clj (log/debug "datascript transaction:" (str "[" duration "ms]")))))

(defn ^:private query [conn query & args]
  (apply d/q query (d/db (first conn)) args))

(defn ^:private init!
  "Initializes the in-memory datascript store with previously transacted data."
  [conn]
  (log/with-duration [{:keys [duration]} (doto conn load-log!)]
    #?(:clj (log/info "datascript initialization:" (str "[" duration "ms]")))))

(defn connect!
  "Connects a client to a database."
  [logger]
  (doto (with-meta [(d/create-conn db/schema)] logger)
    init!))

(defn close!
  "Closes a client's connection to a database."
  [conn]
  #?(:clj (let [{::keys [lock writer]} (meta conn)]
            (locking lock
              (.close writer)))))

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
