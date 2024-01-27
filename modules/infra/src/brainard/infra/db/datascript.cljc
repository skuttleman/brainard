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
     {::db-name db-name
      ::state   (volatile! [])}))

(defn ^:private write! [{::keys [db-name lock state writer]} data]
  #?(:clj  (locking lock
             (.write writer (pr-str data))
             (.append writer \newline)
             (.flush writer))
     :cljs (.setItem js/localStorage db-name (pr-str (vswap! state conj data)))))

(defn ^:private load-log! [conn]
  (let [logger (meta conn)
        conn (first conn)]
    #?(:clj  (let [{::keys [lock log-file]} logger]
               (locking lock
                 (with-open [reader (io/reader (io/file log-file))]
                   (doseq [line (line-seq reader)]
                     (d/transact conn (edn/read-string line))))))
       :cljs (let [{::keys [db-name state]} logger]
               (->> (or (edn/read-string (.getItem js/localStorage db-name)) [])
                    (vreset! state)
                    (run! (partial d/transact! conn)))))))

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
                                           (-> first (d/transact! tx))
                                           (-> meta (write! tx)))]
    #?(:clj (log/debug "datascript transaction:" (str "[" duration "ms]")))))

(defn query
  "Executes a datalog query on the current database value."
  [conn query & args]
  (apply d/q query (d/db (first conn)) args))

(defn init!
  "Initializes the in-memory datascript store with previously transacted data."
  [conn]
  (log/with-duration [{:keys [duration]} (doto conn load-log!)]
    #?(:clj (log/info "datascript initialization:" (str "[" duration "ms]")))))

(defn ^:private do-query [conn {:keys [args only? xform] :as params}]
  (cond-> (->> (apply query conn (:query params) args)
               (walk/postwalk (fn [x]
                                (cond-> x
                                  (map? x) (set/rename-keys {:db/id :brainard/ref}))))
               (sequence (or xform identity)))
    only? first))

(deftype DSStore [conn]
  istorage/IRead
  (read [_ params]
    (do-query conn params))

  istorage/IWrite
  (write! [_ tx]
    (transact! conn tx)))
