(ns brainard.infra.services.datascript
  "This uses an in-memory datascript client which writes transactions to a file to survive refreshes."
  #?(:cljs (:require-macros brainard.infra.services.datascript))
  (:require
    #?(:clj [clojure.java.io :as io])
    [brainard.common.utils.edn :as edn]
    [brainard.common.utils.logger :as log]
    [brainard.infra.services.db :as db]
    [datascript.core :as d]))

(defn file-logger [db-name]
  #?(:cljs {::db-name db-name}
     :clj  (let [file-name (format "resources/private/.ds.%s.log" db-name)]
             {::db-name  db-name
              ::writer   (io/writer (io/file file-name) :append true)
              ::lock     (Object.)
              ::log-file file-name})))

(defn ^:private write! [{::keys [lock writer]} data]
  #?(:clj
     (locking lock
       (.write writer (pr-str data))
       (.append writer \newline)
       (.flush writer))))

(defn ^:private load-log! [conn]
  #?(:clj  (let [{::keys [lock log-file]} (meta conn)]
             (locking lock
               (with-open [reader (io/reader (io/file log-file))]
                 (doseq [line (line-seq reader)]
                   (d/transact (first conn) (edn/read-string line))))))
     :cljs (doseq [line db/ui-log]
             (d/transact (first conn) line))))

(defn connect!
  "Connects a client to a database."
  [logger]
  (with-meta [(d/create-conn db/schema)] logger))

(defn close!
  "Closes a client's connection to a database."
  [conn]
  #?(:clj
     (let [{::keys [lock writer]} (meta conn)]
       (locking lock
         (.close writer)))))

(defn transact!
  "Transacts an arg-map to datascript."
  [conn tx]
  (log/with-duration [{:keys [duration]} (doto conn
                                           (-> meta (write! tx))
                                           (-> first (d/transact! tx)))]
    (log/debug "datascript transaction:" (str "[" duration "ms]"))))

(defn query
  "Executes a datalog query on the current database value."
  [conn query & args]
  (apply d/q query (d/db (first conn)) args))

(defn init!
  "Initializes the in-memory datascript store with previously transacted data."
  [conn]
  (log/with-duration [{:keys [duration]} (doto conn load-log!)]
    (log/info "datascript initialization:" (str "[" duration "ms]"))))
