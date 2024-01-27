(ns brainard.dev
  (:require
    [brainard.app :as app]
    [brainard.infra.store.core :as store]
    [brainard.infra.views.pages.core :as pages]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]
    [whet.core :as w]))

(def ^:dynamic *store*)
(def ^:dynamic *system*)

(defmethod defacto/query-responder ::all
  [db _]
  (select-keys db #{}))

(defn ^:private add-dev-logger! [store]
  (-> store
      (defacto/subscribe [::all])
      (add-watch (gensym)
                 (fn [_ _ _ db]
                   (when (seq db)
                     (print "NEW DB ")
                     (pp/pprint db))))))

(defmethod defacto/event-reducer ::reset
  [_ [_ new-db]]
  new-db)

(defn load!
  "Called when new code is compiled in the browser."
  []
  (let [db-value @*store*]
    (w/render [pages/root *store*]
              (fn []
                (store/emit! *store* [::reset db-value])))))

(defn ^:private with-dev [store]
  (set! *store* store)
  (doto store add-dev-logger!))

(defn init!
  "Called when the DOM finishes loading."
  []
  (set! *system* (app/->system))
  (app/start! *system* (comp app/store->comp with-dev)))
