(ns brainard.ui.core
  (:require
    [brainard.common.store.core :as store]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.pages.core :as pages]
    [brainard.ui.services.navigation.core :as nav]
    [pushy.core :as pushy]
    [reagent.dom :as rdom]
    brainard.common.store.queries
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries))

(defonce ^:private store
  (doto (store/create)
    (store/dispatch! [:resources/submit! :api.tags/select])
    (store/dispatch! [:resources/submit! :api.contexts/select])))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (rdom/render [pages/root store]
               (.getElementById js/document "root")))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (set! nav/pushy-link (letfn [(dispatch [route]
                                 (store/dispatch! store [:routing/navigate route]))]
                         (doto (pushy/pushy dispatch rte/match)
                           pushy/start!)))
  (load!))
