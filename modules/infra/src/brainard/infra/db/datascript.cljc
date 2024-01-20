(ns brainard.infra.db.datascript
  "This uses an in-memory datascript client which writes transactions to a file to survive refreshes."
  #?(:cljs (:require-macros brainard.infra.db.datascript))
  (:require
    #?(:clj [clojure.java.io :as io])
    [brainard.infra.utils.edn :as edn]
    [brainard.api.utils.logger :as log]
    [brainard.resources.db :as db]
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
     {::db-name db-name
      ::state   (volatile! [])}))

(defn ^:private write! [{::keys [db-name lock state writer]} data]
  #?(:clj  (locking lock
             (.write writer (pr-str data))
             (.append writer \newline)
             (.flush writer))
     :cljs (.setItem js/localStorage db-name (pr-str (vswap! state conj data)))))

(defn ^:private load-log! [conn]
  #?(:clj  (let [{::keys [lock log-file]} (meta conn)]
             (locking lock
               (with-open [reader (io/reader (io/file log-file))]
                 (doseq [line (line-seq reader)]
                   (d/transact (first conn) (edn/read-string line))))))
     :cljs (let [{::keys [db-name state]} (meta conn)]
             (vreset! state (edn/read-string (.getItem js/localStorage db-name))))))

(defn connect!
  "Connects a client to a database."
  [logger]
  (with-meta [(d/create-conn db/schema)] logger))

(defn close!
  "Closes a client's connection to a database."
  [conn]
  #?(:clj  (let [{::keys [lock writer]} (meta conn)]
             (locking lock
               (.close writer)))
     :cljs (vreset! (::state (meta conn)) [])))

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
