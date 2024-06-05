(ns brainard.infra.views.pages.shared
  (:require
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [defacto.forms.core :as forms]))

(defn ^:private topic-field [{:keys [*:store form+ on-context-blur sub:contexts]}]
  [:div.layout--space-between
   [:div.flex-grow
    [ctrls/type-ahead (-> {:*:store     *:store
                           :label       "Topic"
                           :sub:items   sub:contexts
                           :auto-focus? true
                           :on-blur     on-context-blur}
                          (ctrls/with-attrs form+ [:notes/context]))]]
   [ctrls/icon-toggle (-> {:*:store *:store
                           :label   "Pinned"
                           :icon    :paperclip}
                          (ctrls/with-attrs form+ [:notes/pinned?]))]])

(defn ^:private body-field [{:keys [*:store form+]}]
  (let [form-data (forms/data form+)]
    [:<>
     [:label.label "Body"]
     [:div {:style {:margin-top 0}}
      (if (::preview? form-data)
        [:div.expanded
         [comp/markdown (:notes/body form-data)]]
        [ctrls/textarea (-> {:style   {:font-family :monospace
                                       :min-height  "250px"}
                             :*:store *:store}
                            (ctrls/with-attrs form+ [:notes/body]))])]]))

(defn note-form [{:keys [*:store form+ sub:tags] :as attrs}]
  [ctrls/form attrs
   [:strong "Create a note"]
   [topic-field attrs]
   [body-field attrs]
   [ctrls/toggle (-> {:label   [:span.is-small "Preview"]
                      :style   {:margin-top 0}
                      :inline? true
                      :*:store *:store}
                     (ctrls/with-attrs form+ [::preview?]))]
   [ctrls/tags-editor (-> {:*:store   *:store
                           :form-id   [::tags (forms/id form+)]
                           :label     "Tags"
                           :sub:items sub:tags}
                          (ctrls/with-attrs form+ [:notes/tags]))]])