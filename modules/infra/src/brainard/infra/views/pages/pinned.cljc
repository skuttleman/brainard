(ns brainard.infra.views.pages.pinned
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [defacto.forms.core :as forms]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private collapsible [{:keys [*:store expanded? expand]} label & content]
  (cond-> [:div [:div.layout--row
                 [:div.layout--space-after label]
                 [comp/plain-button {:class ["is-small" "is-white"]
                                     :on-click (fn [_]
                                                 (store/emit! *:store expand))}
                  [comp/icon (if expanded? :chevron-up :chevron-down)]]]]
    expanded? (into content)))

(defn ^:private root [*:store sub:form route-info notes]
  (let [form @sub:form
        form-data (forms/data form)
        form-id (forms/id form)]
    [:div
     (if (seq notes)
       (for [[context group] (group-by :notes/context notes)
             :let [expanded? (= context (::context form-data))
                   next-context (when-not expanded? context)]]
         ^{:key context}
         [collapsible {:*:store *:store
                       :expanded? expanded?
                       :expand    [::forms/changed form-id [::context] next-context]}
          [:strong context]
          [:div {:style {:margin-left "12px"}}
           [notes.views/note-list (assoc route-info :skip-context? true) group]]])
       [:em "No pinned notes"])]))

(defmethod ipages/page :routes.ui/pinned
  [*:store route-info]
  (r/with-let [sub:pinned (do (store/dispatch! *:store [::res/ensure! [::specs/notes#pinned]])
                              (store/subscribe *:store [::res/?:resource [::specs/notes#pinned]]))
               sub:form (do (store/dispatch! *:store [::forms/ensure! [::expanded-group]])
                                (store/subscribe *:store [::forms/?:form [::expanded-group]]))]
    [comp/with-resource sub:pinned [root *:store sub:form route-info]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/notes#pinned]])
      (store/emit! *:store [::forms/destroyed [::expanded-group]]))))
