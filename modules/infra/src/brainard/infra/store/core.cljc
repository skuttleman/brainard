(ns brainard.infra.store.core
  #?(:cljs (:require-macros brainard.infra.store.core))
  (:require
    [defacto.core :as defacto]
    [whet.utils.reagent :as r]))

(defn create
  ([]
   (create nil))
  ([ctx]
   (create ctx nil))
  ([ctx db-value]
   (defacto/create ctx db-value {:->sub r/atom})))

(defn dispatch! [store command]
  (defacto/dispatch! store command))

(defn emit! [store event]
  (defacto/emit! store event))

(defn subscribe [store query]
  (defacto/subscribe store query))

(defn query [store query]
  (defacto/query-responder @store query))
