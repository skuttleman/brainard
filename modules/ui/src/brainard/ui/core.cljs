(ns brainard.ui.core
  (:require
    [brainard.common.services.store.core :as store]
    [brainard.common.views.pages.core :as pages]
    [reagent.dom :as rdom]
    brainard.common.services.store.registration))

(defn load []
  (rdom/render [pages/root]
               (.getElementById js/document "root")))

(defn init []
  (enable-console-print!)
  (store/dispatch-sync [:core/init])
  (store/dispatch [:resources/submit! :api.tags/select])
  (store/dispatch [:resources/submit! :api.contexts/select])
  (load))
