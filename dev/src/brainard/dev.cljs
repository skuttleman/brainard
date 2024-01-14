(ns brainard.dev
  (:require
    [brainard.app :as app]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [clojure.core.async :as async]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [whet.core :as w]))

(def ^:dynamic *store*)

(defmethod defacto/query-responder ::all
  [db _]
  (select-keys db #{:defacto.forms.core/-forms}))

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
  #_
  (let [db-value @*store*]
  (app/load! *store*
             (fn []
                 (store/emit! *store* [::reset db-value])))))

(defn ^:private after-render [store]
  (doto store
    add-dev-logger!
    (defacto/dispatch! [::res/submit! [::specs/notes#buzz]])
    (defacto/dispatch! [::res/submit! [::specs/tags#select]])
    (defacto/dispatch! [::res/submit! [::specs/contexts#select]]))
  (async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! store [::res/poll! 15000 [::specs/notes#buzz]])))

(defn init! []
  #_#_(set! *store* (doto (app/->store) add-dev-logger!))
  (app/init! *store*)
  (enable-console-print!)
  (w/render-ui rte/all-routes pages/root after-render))
