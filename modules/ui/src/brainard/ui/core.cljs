(ns brainard.ui.core
  (:require
    [brainard.common.services.store.core :as store]
    [brainard.common.views.pages.core :as pages]
    [reagent.dom :as rdom]
    brainard.common.services.store.registration))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (rdom/render [pages/root]
               (.getElementById js/document "root")))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (store/dispatch-sync [:core/init])
  (store/dispatch [:resources/submit! :api.tags/select])
  (store/dispatch [:resources/submit! :api.contexts/select])
  (load!))
