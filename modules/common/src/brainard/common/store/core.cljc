(ns brainard.common.store.core
  (:require
    [defacto.core :as defacto]
    [clojure.pprint :as pp]))

(defmethod defacto/query ::spy
  [db _]
  (select-keys db #{}))

(defn ^:private add-dev-logger! [store]
  (add-watch (defacto/subscribe store [::spy])
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
   (let [store (doto (defacto/create ctx db-value)
                 add-dev-logger!)]

     store)))

(defn dispatch! [store command]
  (defacto/dispatch! store command))

(defn subscribe [store query]
  (defacto/subscribe store query))
