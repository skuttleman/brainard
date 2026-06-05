(ns brainard.test.harness.integration.system
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.db.store :as ds]
    [brainard.main :as main]
    [clojure.java.io :as io]
    [clojure.test :refer [testing]]
    [datomic.client.api :as d]
    [integrant.core :as ig]
    brainard.dev.s3
    brainard.infra.system.core
    brainard.test))

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

(defmethod ig/init-key :brainard.test/fs-invoker
  [_ _]
  (let [path (str ".obj-storage/test/" (uuids/random))
        invoker (ig/init-key :brainard/fs-invoker {:path path})]
    (vary-meta invoker assoc ::path path)))

(defmethod ig/halt-key! :brainard.test/fs-invoker
  [_ invoker]
  (let [objects (:Contents (invoker {:op :ListObjectsV2}))]
    (invoker {:op      :DeleteObjects
              :request {:Delete {:Objects objects}}})
    (-> invoker meta ::path io/file io/delete-file)))

(defmethod istorage/->input :default
  [params]
  params)

(defmacro with-app [[sys-binding opts] & body]
  (let [sys (gensym)
        test (str (gensym "testing with-app "))
        component-bindings (for [[k v] (when (map? sys-binding)
                                         sys-binding)
                                 :let [bindings (cond
                                                  (and (keyword? k) (= (name k) "keys"))
                                                  (map #(vector % (keyword (namespace k) (name %))) v)

                                                  (and (symbol? k) (keyword? v) (namespace v))
                                                  [[k v]])]
                                 [sym kw] bindings
                                 token [sym `(val (ig/find-derived-1 ~sys ~kw))]]
                             token)]
    `(let [opts# ~opts
           ~sys (main/start! (:config opts# "duct/test.edn")
                             [:duct.profile/base :duct.profile/test]
                             (:init-keys opts# [:brainard/apis]))
           ~sys-binding ~sys
           ~@component-bindings]
       (try
         (testing ~test
           ~@body)
         (finally
           (ig/halt! ~sys))))))

