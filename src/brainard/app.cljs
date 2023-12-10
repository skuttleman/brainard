(ns brainard.app
  (:require
    [brainard.common.resources.specs :as rspecs]
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
  (assoc db :app/loading? status))

(defn ^:private load* []
  (let [root (.getElementById js/document "root")]
    (rdom/render [pages/root *store*]
                 root
                 (fn []
                   (store/emit! *store* [::loading-changed false])))))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (store/emit! *store* [::loading-changed true])
  (load*))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (set! *store* (store/create {:services/http http/request
                               :services/nav  (->NavComponent nil)}
                              (:init-db dom/env)))
  (defacto/dispatch! *store* [:resources/quietly! ::rspecs/notes#poll])
  (load*))
