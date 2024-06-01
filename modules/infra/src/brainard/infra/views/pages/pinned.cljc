(ns brainard.infra.views.pages.pinned
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [clojure.set :as set]
    [defacto.forms.core :as forms]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private collapsible [{:keys [*:store expanded? expand]} label & content]
  (cond-> [:div [:div.layout--row
                 [:div.layout--space-after label]
                 [comp/plain-button {:class    ["is-small" "is-white"]
                                     :on-click (fn [_]
                                                 (store/emit! *:store expand))}
                  [comp/icon (if expanded? :chevron-up :chevron-down)]]]]
    expanded? (into content)))

(defn ^:private tag-filter [{:keys [*:store form]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [ctrls/multi-dropdown (-> {:*:store       *:store
                               :inline?       true
                               :label         "Filter by tags"
                               :options       options
                               :options-by-id options-by-id}
                              (ctrls/with-attrs form [::tag-filters]))]))

(defn ^:private root [*:store sub:form route-info [tags pinned-notes]]
  (let [form @sub:form
        {::keys [expanded tag-filters]} (forms/data form)
        form-id (forms/id form)
        filtered-notes (filter (fn [{:notes/keys [tags]}]
                                 (set/subset? tag-filters tags))
                               pinned-notes)]
    [:div
     [tag-filter {:*:store *:store
                  :form    form}
      tags]
     (if (seq filtered-notes)
       (for [[context note-group] (group-by :notes/context filtered-notes)
             :let [expanded? (= context expanded)
                   next-context (when-not expanded? context)]]
         ^{:key context}
         [collapsible {:*:store   *:store
                       :expanded? expanded?
                       :expand    [::forms/changed form-id [::expanded] next-context]}
          [:strong context]
          [:div {:style {:margin-left "12px"}}
           [notes.views/note-list (assoc route-info :skip-context? true) note-group]]])
       [:em "No pinned notes"])]))

(defmethod ipages/page :routes.ui/pinned
  [*:store route-info]
  (r/with-let [sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:pinned (do (store/dispatch! *:store [::res/ensure! [::specs/notes#pinned]])
                              (store/subscribe *:store [::res/?:resource [::specs/notes#pinned]]))
               sub:form (do (store/dispatch! *:store [::forms/ensure! [::expanded-group] {::tag-filters #{}}])
                            (store/subscribe *:store [::forms/?:form [::expanded-group]]))]
    [comp/with-resources [sub:tags sub:pinned] [root *:store sub:form route-info]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/notes#pinned]])
      (store/emit! *:store [::forms/destroyed [::expanded-group]]))))
