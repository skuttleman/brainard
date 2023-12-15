(ns brainard.app
  (:require
    [brainard.common.resources.specs :as rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.pages.core :as pages]
    [cljs-http.client :as http]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [pushy.core :as pushy]
    [reagent.dom :as rdom]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries))

(def ^:private ^:dynamic *store*)

(deftype NavComponent [^:volatile-mutable -pushy]
  defacto/IInitialize
  (init! [_ store]
    (let [pushy (pushy/pushy #(store/emit! store [:routing/navigated %]) rte/match)]
      (set! -pushy pushy)
      (pushy/start! pushy)))

  nav/INavigate
  (-set! [_ uri]
    (pushy/set-token! -pushy uri))
  (-replace! [_ uri]
    (pushy/replace-token! -pushy uri)))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn ^:private merger [row1 row2]
  (if (and (map? row1) (map? row2))
    (merge-with conj row1 row2)
    (or row2 row1)))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) merger nil warnings))

(defn ^:private request-fn [_ params]
  (async/go
    (let [{:keys [status body]} (async/<! (http/request params))]
      (if (success? status)
        [::res/ok (:data body)]
        [::res/err (remote->warnings (:errors body))]))))

(defmethod defacto/event-reducer ::reset
  [_ [_ new-db]]
  new-db)

(defn ^:private load* [cb]
  (let [root (.getElementById js/document "root")]
    (rdom/render [pages/root *store*] root cb)))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (let [db-value @*store*]
    (load* (fn []
             (store/emit! *store* [::reset db-value])))))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (set! *store* (store/create (-> {:services/nav (->NavComponent nil)}
                                  (res/with-ctx request-fn))
                              (:init-db dom/env)))
  #_(async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! *store* [::res/poll! 15000 [::rspecs/notes#buzz]]))
  (load* (constantly nil)))
