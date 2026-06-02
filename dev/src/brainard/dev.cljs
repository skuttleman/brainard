(ns brainard.dev
  (:require
    [brainard.api.utils.logger :as log]
    [brainard.app :as app]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.pages.core :as pages]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]
    [whet.core :as w]))

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
  (doto store add-dev-logger!))

(defn ^:private handler-mw [handler ctx [action :as cmd] emit-cb]
  #_(when-not (#{:defacto.core/emit!} action)
      (log/info "command:" action))
  (handler ctx cmd emit-cb))

(defn ^:private reducer-mw [reducer db [type :as event]]
  #_(if (contains? (methods defacto.core/event-reducer) type)
      (log/info "event:  " type)
      (log/warn "UNevent:" type))
  (reducer db event))

(def ^:private store-mw
  {:handler-mw (fn [handler]
                 (fn [ctx cmd emit-cb]
                   (handler-mw handler ctx cmd emit-cb)))
   :reducer-mw (fn [reducer]
                 (fn [db event]
                   (reducer-mw reducer db event)))})

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
  (log/info "dev app initialized")
  (app/start! (comp app/store->comp with-dev) store-mw))
