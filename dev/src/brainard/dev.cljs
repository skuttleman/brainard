(ns brainard.dev
  (:require
    [brainard.app :as app]
    [brainard.infra.store.core :as store]
    [defacto.core :as defacto]))

(def ^:dynamic *store*)

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
  (set! *store* (app/->store))
  (app/init! *store*))
