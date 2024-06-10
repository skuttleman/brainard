(ns brainard.infra.db.store
  "This uses a datomic local client"
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [#?(:clj datomic.client.api :default datascript.core) :as d]))

#?(:cljs
   (defn ^:private write! [{::keys [db-name]} conn]
     (.setItem js/localStorage db-name (-> (d/db (first conn))
                                           d/serializable
                                           js/JSON.stringify))))

#?(:cljs
   (defn ^:private load-log! [conn]
     (let [{::keys [db-name]} (meta conn)
           conn (first conn)]
       (when-let [db (some-> (.getItem js/localStorage db-name)
                             js/JSON.parse
                             d/from-serializable)]
         (d/reset-conn! conn db)))))

#?(:cljs
   (defn ^:private ->ds-schema [schema]
     (into {}
           (map (juxt :db/ident
                      (fn [spec]
                        (-> spec
                            (select-keys #{:db/cardinality :db/unique :db/doc :db/isComponent})
                            (cond->
                              (= :db.type/ref (:db/valueType spec))
                              (assoc :db/valueType :db.type/ref))))))
           schema)))

(defn ^:private transact! [conn tx]
  #?(:clj  (d/transact (first conn) {:tx-data tx})
     :cljs (doto conn
             (-> first (d/transact! tx))
             (-> meta (write! conn)))))

(defn ^:private query [conn {:keys [args only? query ref? xform]}]
  (cond-> (->> (apply d/q query (d/db (first conn)) args)
               (walk/postwalk (fn [x]
                                (cond-> x
                                  (map? x) (cond->
                                             ref? (set/rename-keys {:db/id :brainard/ref})
                                             (not ref?) (dissoc :db/id)))))
               (sequence (or xform identity)))
    only? first))

(defn connect! [{:keys [storage-dir db-name schema]}]
  #?(:clj  (let [client (d/client {:server-type :datomic-local
                                   :storage-dir (or storage-dir
                                                    (format "%s/.datomic-storage"
                                                            (System/getProperty "user.dir")))
                                   :system      "main"})]
             (d/create-database client {:db-name db-name})
             [(doto (d/connect client {:db-name db-name})
                (d/transact {:tx-data schema}))])
     :cljs (doto (with-meta [(d/create-conn (->ds-schema schema))] {::db-name db-name})
             load-log!)))

(deftype DSStore [conn]
  istorage/IRead
  (read [_ params]
    (query conn params))

  istorage/IWrite
  (write! [_ tx]
    (transact! conn tx)))