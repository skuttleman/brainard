(ns brainard.dev
  (:require
    [brainard.app :as app]
    [brainard.infra.store.core :as store]
    [cljs.pprint :as pp]
    [defacto.core :as defacto]))

(def ^:dynamic *store*)

(defmethod defacto/query-responder ::all
  [db _]
  (select-keys db #{}))

(defn ^:private add-dev-logger! [store]
  (doto store
    (-> (defacto/subscribe [::all])
        (add-watch (gensym)
                   (fn [_ _ _ db]
                     (when (seq db)
                       (print "NEW DB ")
                       (pp/pprint db)))))))

(defmethod defacto/event-reducer ::reset
  [_ [_ new-db]]
  new-db)

(defn load!
  "Called when new code is compiled in the browser."
  []
  (let [db-value @*store*]
    (app/load! *store*
               (fn []
                 (store/emit! *store* [::reset db-value])))))

(defn init! []
  (set! *store* (doto (app/->store) add-dev-logger!))
  (app/init! *store*))
