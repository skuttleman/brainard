(ns brainard.common.store.core
  (:require
    [brainard.common.stubs.reagent :as r]
    [defacto.core :as defacto]
    [clojure.pprint :as pp]))

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
   (doto (defacto/create ctx db-value r/atom)
     add-dev-logger!)))

(defn dispatch! [store command]
  (defacto/dispatch! store command))

(defn emit! [store event]
  (dispatch! store [::defacto/emit! event]))

(defn subscribe [store query]
  (defacto/subscribe store query))
