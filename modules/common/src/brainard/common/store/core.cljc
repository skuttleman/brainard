(ns brainard.common.store.core
  (:require
    [yast.core :as yast]
    [clojure.pprint :as pp]))

(defmethod yast/query ::spy
  [db _]
  (select-keys db #{}))

(defn ^:private add-dev-logger! [store]
  (add-watch (yast/subscribe store [::spy])
             (gensym)
             (fn [_ _ _ db]
               (when (seq db)
                 (print "NEW DB ")
                 (pp/pprint db)))))

(defn create
  ([]
   (create nil))
  ([db-value]
   (let [store (doto (yast/create db-value)
                 add-dev-logger!)]

     store)))

(defn dispatch! [store command]
  #_(println "EMITTING!" command)
  (yast/dispatch! store command))

(defn subscribe [store query]
  (yast/subscribe store query))
