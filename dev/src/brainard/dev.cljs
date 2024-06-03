(ns brainard.dev
  (:require
    [brainard.app :as app]
    [brainard.pages.dev :as dev]
    [brainard.infra.store.core :as store]
    [brainard.infra.views.pages.core :as pages]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]
    [whet.core :as w]))

(def ^:dynamic *store*)

(defmethod defacto/event-reducer ::reset
  [_ [_ new-db]]
  new-db)

(defmethod defacto/query-responder ::spy
  [db _]
  (select-keys db #{}))

(defn ^:private reducer-mw [reducer]
  (fn [db event]
    (let [occurred-at (js/Date.)
          id (random-uuid)]
      (cond-> (reducer db event)
        (not (:skip-tracking? (meta event)))
        (update ::dev/-events
                (fnil conj ())
                (vary-meta event assoc ::dev/id id ::dev/occurred-at occurred-at))))))

(defn ^:private add-dev-logger! [store]
  (-> store
      (defacto/subscribe [::spy])
      (add-watch (gensym)
                 (fn [_ _ _ db]
                   (when (seq db)
                     (print "NEW DB ")
                     (pp/pprint db))))))

(defn ^:private with-dev [store]
  (set! *store* store)
  (doto store add-dev-logger!))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (let [db-value @*store*]
    (w/render [pages/root *store*]
              (fn []
                (store/emit! *store* [::reset db-value])))))

(defn init!
  "Called when the DOM finishes loading."
  []
  (app/start! (comp app/store->comp with-dev)
              {:reducer-mw reducer-mw}))
