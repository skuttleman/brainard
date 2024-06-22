(ns brainard.dev
  (:require
    [brainard.app :as app]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.pages.core :as pages]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]
    [whet.core :as w]
    #_brainard.stubbed.api))

(defonce ^:dynamic *store* nil)

(defmethod defacto/event-reducer ::reset
  [_ [_ new-db]]
  new-db)

(defmethod defacto/query-responder ::spy
  [db _]
  (select-keys db #{}))

(defn ^:private add-dev-logger! [store]
  (-> store
      (store/subscribe [::spy])
      (add-watch (gensym)
                 (fn [_ _ _ db]
                   (when (seq db)
                     (print "NEW DB ")
                     (pp/pprint db))))))

(defn ^:private with-dev [store]
  (set! *store* store)
  #_(doto store
    (store/dispatch! [::res/submit! [::specs/notes#buzz]])
    (store/dispatch! [::res/submit! [::specs/tags#select]])
    (store/dispatch! [::res/submit! [::specs/contexts#select]]))
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
  (app/start! (comp app/store->comp with-dev)))
