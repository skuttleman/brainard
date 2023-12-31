(ns brainard.common.store.core
  #?(:cljs (:require-macros brainard.common.store.core))
  (:require
    [brainard.common.stubs.reagent :as r]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]))

(defmethod defacto/query-responder ::all
  [db _]
  (select-keys db #{}))

(defn ^:private add-dev-logger! [store]
  (add-watch (defacto/subscribe store [::all])
             (gensym)
             (fn [_ _ _ db]
               (when (seq db)
                 (print "NEW DB ")
                 (pp/pprint db)))))

(defn create
  ([]
   (create nil))
  ([ctx]
   (create ctx nil))
  ([ctx db-value]
   (doto (defacto/create ctx db-value {:->sub r/atom})
     add-dev-logger!)))

(defn dispatch! [store command]
  (defacto/dispatch! store command))

(defn emit! [store event]
  (defacto/emit! store event))

(defn subscribe [store query]
  (defacto/subscribe store query))

(defn query [store query]
  (defacto/query-responder @store query))
