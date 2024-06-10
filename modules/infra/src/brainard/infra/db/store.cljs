(ns brainard.infra.db.store
  "This uses an in-memory datascript client which writes transactions to localStorage"
  (:require-macros brainard.infra.db.store)
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [datascript.core :as d]))

(defn ^:private write! [{::keys [db-name]} conn]
  (.setItem js/localStorage db-name (-> (d/db (first conn))
                                        d/serializable
                                        js/JSON.stringify)))

(defn ^:private load-log! [conn]
  (let [{::keys [db-name]} (meta conn)
        conn (first conn)]
    (when-let [db (some-> (.getItem js/localStorage db-name)
                          js/JSON.parse
                          d/from-serializable)]
      (d/reset-conn! conn db))))

(defn ^:private transact! [conn tx]
  (doto conn
    (-> first (d/transact! tx))
    (-> meta (write! conn))))

(defn ^:private do-query [conn {:keys [args only? xform ref?] :as params}]
  (cond-> (->> (apply d/q (:query params) (d/db (first conn)) args)
               (walk/postwalk (fn [x]
                                (cond-> x
                                  (map? x) (cond->
                                             ref? (set/rename-keys {:db/id :brainard/ref})
                                             (not ref?) (dissoc :db/id)))))
               (sequence (or xform identity)))
    only? first))

(defn ^:private ->ds-schema [schema]
  (into {}
        (map (juxt :db/ident
                   (fn [spec]
                     (-> spec
                         (select-keys #{:db/cardinality :db/unique :db/doc :db/isComponent})
                         (cond->
                           (= :db.type/ref (:db/valueType spec))
                           (assoc :db/valueType :db.type/ref))))))
        schema))

(defn ->conn [_client db-name schema]
  (doto (with-meta [(d/create-conn (->ds-schema schema))] {::db-name db-name})
    load-log!))

(defn ->client [_storage-dir]
  nil)

(deftype DSStore [conn]
  istorage/IRead
  (read [_ params]
    (do-query conn params))

  istorage/IWrite
  (write! [_ tx]
    (transact! conn tx)))
