(ns brainard.infra.views.pages.buzz
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defmethod ipages/page :routes.ui/buzz
  [{:keys [*:store] :as route-info}]
  (r/with-let [sub:notes (store/subscribe *:store [::res/?:resource [::specs/notes#buzz]])]
    (let [resource @sub:notes]
      [:div
       [:h2.subtitle "What's relevant now?"]
       (if (or (res/requesting? resource) (res/success? resource))
         [notes.views/search-results route-info (res/payload resource)]
         [comp/spinner])])))
