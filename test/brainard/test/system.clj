(ns brainard.test.system
  (:require
    [brainard.common.utils.uuids :as uuids]
    [brainard.infra.services.datomic :as-alias datomic]
    [duct.core :as duct]
    [integrant.core :as ig])
  (:import
    (org.apache.commons.io.output NullWriter)))

(defmethod ig/init-key :brainard.test/null-logger
  [_ {:keys [db-name]}]
  {::datomic/db-name  db-name
   ::datomic/log-file "/dev/null"
   ::datomic/writer   NullWriter/NULL_WRITER
   ::datomic/lock     (Object.)})

(defmethod ig/init-key :brainard.test/db-name
  [_ _]
  (str (uuids/random)))

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
