(ns brainard.resources.db
  #?(:cljs (:require-macros brainard.resources.db))
  (:require
    [brainard.infra.utils.edn :as edn]))

(defmacro schema []
  `(let [data# ~(edn/resource "schema.edn")]
     (into {}
           (map (juxt :db/ident
                      (fn [spec#]
                        (-> spec#
                            (select-keys #{:db/cardinality :db/unique :db/doc :db/isComponent})
                            (cond->
                              (= :db.type/ref (:db/valueType spec#))
                              (assoc :db/valueType :db.type/ref))))))
           data#)))
