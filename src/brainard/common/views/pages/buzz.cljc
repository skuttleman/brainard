(ns brainard.common.views.pages.buzz
  (:require
    [brainard.common.resources.specs :as rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.pages.interfaces :as ipages]
    [brainard.common.views.pages.shared :as spages]
    [defacto.resources.core :as res]))

(defmethod ipages/page :routes.ui/buzz
  [{:keys [*:store] :as route-info}]
  (r/with-let [sub:notes (store/subscribe *:store [::res/?:resource [::rspecs/notes#buzz]])]
    (let [resource @sub:notes]
      [:div
       [:h2.subtitle "What's relevant now?"]
       (if (or (res/requesting? resource) (res/success? resource))
         [spages/search-results route-info (res/payload resource)]
         [comp/spinner])])))
