(ns brainard.infra.views.pages.pinned
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private root [route-info notes]
  [:div
   (if (seq notes)
     (for [[context group] (group-by :notes/context notes)]
       ^{:key context}
       [comp/collapsible
        [:strong context]
        [:div {:style {:margin-left "12px"}}
         [notes.views/note-list (assoc route-info :skip-context? true) group]]])
     [:em "No pinned notes"])])

(defmethod ipages/page :routes.ui/pinned
  [*:store route-info]
  (r/with-let [sub:pinned (do (store/dispatch! *:store [::res/ensure! [::specs/notes#pinned]])
                            (store/subscribe *:store [::res/?:resource [::specs/notes#pinned]]))]
    [comp/with-resource sub:pinned [root route-info]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/notes#pinned]]))))
