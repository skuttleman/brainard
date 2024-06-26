(ns brainard.test.system
  (:require
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.db.store :as ds]
    [datomic.client.api :as d]
    [duct.core :as duct]
    [integrant.core :as ig]))

(defmethod ig/init-key :brainard.test/db-name
  [_ _]
  (str (uuids/random)))

(defmethod ig/init-key :brainard.test/db-conn
  [_ input]
  (ds/connect! input))

(defmethod ig/halt-key! :brainard.test/db-conn
  [_ conn]
  (let [{::ds/keys [db-name]} (meta conn)
        client (second conn)]
    (d/delete-database client {:db-name db-name})))

(defmacro with-system [[sys-binding opts] & body]
  (let [sys (gensym)
        component-bindings (for [[k v] (when (map? sys-binding)
                                         sys-binding)
                                 :let [bindings (cond
                                                  (and (keyword? k) (= (name k) "keys"))
                                                  (map #(vector % (keyword (namespace k) (name %))) v)

                                                  (and (symbol? k) (keyword v))
                                                  [[k v]])]
                                 [sym k] bindings
                                 token [sym `(val (ig/find-derived-1 ~sys ~k))]]
                             token)]
    `(let [opts# ~opts
           _# (duct/load-hierarchy)
           ~sys (-> (:config opts# "duct/test.edn")
                    duct/resource
                    duct/read-config
                    (duct/prep-config [:duct.profile/base :duct.profile/test])
                    (ig/init (:init-keys opts# [:brainard/apis])))
           ~sys-binding ~sys
           ~@component-bindings]
       (try
         ~@body
         (finally
           (ig/halt! ~sys))))))
