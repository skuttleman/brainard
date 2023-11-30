(ns brainard.common.views.pages.notes
  (:require
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.main :as views.main]))

(defn root* [note]
  [:div.layout--stack-between
   [:h1 [:em (:notes/context note)]]
   [:p (:notes/body note)]
   [ctrls/tag-list {:value (:notes/tags note)}]])

(defn root [{:keys [route-params]}]
  (r/with-let [sub:note (do (rf/dispatch [:resources/submit! [:api.notes/find (:notes/id route-params)]])
                            (rf/subscribe [:resources/resource [:api.notes/find (:notes/id route-params)]]))]
    [views.main/with-resource sub:note [root*]]
    (finally
      (rf/dispatch [:resources/destroy [:api.notes/find (:notes/id route-params)]]))))
