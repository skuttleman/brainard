(ns brainard.app
  (:require
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.pages.core :as pages]
    [cljs-http.client :as http]
    [pushy.core :as pushy]
    [reagent.dom :as rdom]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries))

(def ^:private ^:dynamic *nav*)

(defonce ^:private store
  (delay
    (store/create {:services/http http/request
                   :services/nav  *nav*}
                  (:init-db dom/env))))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (let [root (.getElementById js/document "root")]
    (rdom/render [pages/root @store] root)))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (set! *nav* (pushy/pushy #(store/emit! @store [:routing/navigated %]) rte/match))
  (pushy/start! *nav*)
  (load!))
