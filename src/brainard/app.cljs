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
(def ^:private ^:dynamic *nav*)

(deftype NavComponent [^:volatile-mutable -pushy]
  defacto/IInitialize
  (init! [_ store]
    (when -pushy (pushy/stop! -pushy))
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
        [:ok (:data body)]
        [:err (remote->warnings (:errors body))]))))

(defn ^:private load* [init-db]
  (let [root (.getElementById js/document "root")]
    (set! *store* (store/create (-> {:services/nav *nav*}
                                    (res/with-ctx request-fn))
                                init-db))
    (rdom/render [pages/root *store*] root)))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (load* @*store*))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (set! *nav* (->NavComponent nil))
  (async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! *store* [::res/poll! 15000 [::rspecs/notes#buzz]]))
  (load* (:init-db dom/env)))
