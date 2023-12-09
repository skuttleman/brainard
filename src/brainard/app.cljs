(ns brainard.app
  (:require
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.pages.core :as pages]
    [cljs-http.client :as http]
    [defacto.core :as defacto]
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

(defmethod defacto/event-reducer ::loading-changed
  [db [_ status]]
  (assoc db ::loading? status))

(defmethod defacto/query-responder :app/?:loading
  [db _]
  (::loading? db false))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (let [root (.getElementById js/document "root")]
    (store/emit! *store* [::loading-changed true])
    (rdom/render [pages/root *store*]
                 root
                 (fn []
                   (store/emit! *store* [::loading-changed false])))))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (set! *store* (store/create {:services/http http/request
                               :services/nav  (->NavComponent nil)}
                              (:init-db dom/env)))
  (load!))
