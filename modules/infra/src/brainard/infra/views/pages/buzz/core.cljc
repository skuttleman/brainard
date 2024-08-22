(ns brainard.infra.views.pages.buzz.core
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defmethod ipages/page :routes.ui/buzz
  [*:store _]
  (r/with-let [sub:notes (store/res-sub *:store [::specs/notes#buzz] {::res/ttl 5000})]
    (let [resource @sub:notes]
      [:div
       [:h2.subtitle "What's relevant now?"]
       (if (or (res/requesting? resource) (res/success? resource))
         [notes.views/note-list {} (res/payload resource)]
         [comp/spinner])])))
