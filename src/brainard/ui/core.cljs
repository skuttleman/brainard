(ns brainard.ui.core
  (:require
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.views.pages.core :as pages]
    [reagent.dom :as rdom]
    brainard.ui.services.store.core))

(defn load []
  (rdom/render [pages/root]
               (.getElementById js/document "root")))

(defn init []
  (enable-console-print!)
  (rf/dispatch-sync [:core/init])
  (rf/dispatch [:api.tags/fetch])
  (rf/dispatch [:api.contexts/fetch])
  (load))
