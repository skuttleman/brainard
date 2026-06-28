(ns brainard.test.harness.integration.system
  (:require
   [brainard.api.events.interfaces :as ievents]
   [brainard.api.storage.interfaces :as istorage]
   [brainard.dev.s3 :as s3]
   [brainard.infra.db.store :as ds]
   [brainard.infra.search.store :as search]
   [brainard.main :as main]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.test :refer [testing]]
   [datomic.client.api :as d]
   [integrant.core :as ig]
   [slag.utils.uuids :as uuids]
   brainard.infra.system.core))

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
  (let [db-name (str (uuids/random))]
    (ig/init-key :brainard/fs-invoker {:db-name db-name})))

(defmethod ig/halt-key! :brainard.test/fs-invoker
  [_ invoker]
  (let [objects (:Contents (invoker {:op :ListObjectsV2}))]
    (invoker {:op      :DeleteObjects
              :request {:Delete {:Objects objects}}})
    (-> invoker meta ::s3/path io/file io/delete-file)
    (-> invoker meta ::s3/path-prefix io/file io/delete-file)))

(defmethod ig/init-key :brainard.test/search-index
  [_ _]
  (search/->mem-index))

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
           timeout# (:timeout opts# 2000)
           event-ch# (async/chan 10 (map second))
           ~sys (-> (main/start! (:config opts# "duct/test.edn")
                                 [:duct.profile/base :duct.profile/test]
                                 (:init-keys opts# [:brainard/apis]))
                    (assoc :brainard/event-ch event-ch#))
           ~sys-binding ~sys
           ~@component-bindings]
       (try
         (when-let [[_# events#] (ig/find-derived-1 ~sys :brainard/events)]
           (ievents/connect! events# ::events {:ch event-ch#}))
         (testing ~test
           (let [f# (future ~@body)]
             (when (= ::timeout (deref f# timeout# ::timeout))
               (future-cancel f#)
               (throw (ex-info "test timed out" {:timeout timeout#})))))
         (finally
           (async/close! event-ch#)
           (ig/halt! ~sys))))))
