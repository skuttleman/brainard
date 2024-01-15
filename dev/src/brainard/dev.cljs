(ns brainard.dev
  (:require
    [brainard.app :as app]
    [brainard.infra.store.core :as store]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]
    [whet.core :as w]))

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
    (w/render [pages/root *store*]
              (fn []
                (store/emit! *store* [::reset db-value])))))

(defn ^:private after-render [store]
  (set! *store* store)
  (add-dev-logger! store)
  (app/on-rendered store))

(defn init!
  "Called when the DOM finishes loading."
  []
  (w/render-ui rte/all-routes pages/root after-render))
