(ns brainard.infra.services.datomic
  (:require
    [brainard.common.utils.edn :as edn]
    [brainard.common.utils.logger :as log]
    [clojure.java.io :as io]
    [datomic.client.api :as d]))

(def ^:private ^{:arglists '([file-name])} file-logger
  (memoize (fn [file-name]
             {::writer (io/writer (io/file file-name) :append true)
              ::lock   (Object.)
              ::log-file file-name})))

(defn ^:private write! [{::keys [lock writer]} data]
  (locking lock
    (.write writer (pr-str data))
    (.append writer \newline)
    (.flush writer)))

(defn ^:private load-schema! [conn schema-file]
  (d/transact (first conn) {:tx-data (edn/resource schema-file)}))

(defn ^:private load-log! [conn]
  (let [{::keys [lock log-file]} (meta conn)]
    (locking lock
      (with-open [reader (io/reader (io/file log-file))]
        (doseq [line (line-seq reader)]
          (d/transact (first conn) (edn/read-string line)))))))

(defn create-client [params]
  (d/client params))

(defn create-database [client db-name]
  (d/create-database client {:db-name db-name}))

(defn connect! [client db-name log-file]
  (with-meta [(d/connect client {:db-name db-name})]
             (file-logger log-file)))

(defn close! [conn]
  (let [{::keys [lock writer]} (meta conn)]
    (locking lock
      (.close writer))))

(defn transact! [conn arg-map]
  (log/with-duration [{:keys [duration]} (doto conn
                                           (-> meta (write! arg-map))
                                           (-> first (d/transact arg-map)))]
    (log/debug "datomic transaction:" (str "[" duration "ms]"))))

(defn query [conn query & args]
  (apply d/q query (d/db (first conn)) args))

(defn init! [conn schema-file]
  (log/with-duration [{:keys [duration]} (doto conn
                                           (load-schema! schema-file)
                                           load-log!)]
    (log/info "datomic initialization:" (str "[" duration "ms]"))))
