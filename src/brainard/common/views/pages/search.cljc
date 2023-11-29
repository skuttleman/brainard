(ns brainard.common.views.pages.search
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.main :as views.main]))

(defn ^:private item-control [item]
  [:span item])

(defn ^:private context-filter [{:keys [form form-id]} contexts]
  (r/with-let [options (map #(vector % %) contexts)
               options-by-id (into {} options)]
    [:div {:style {:flex-grow 1}}
     [ctrls/single-dropdown {:label         "Context filter"
                             :options       options
                             :options-by-id options-by-id
                             :value         (:context (forms/data form))
                             :on-change     [:forms/change form-id [:context]]
                             :item-control  item-control}]]))

(defn ^:private tag-filter [{:keys [form form-id]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [:div {:style {:flex-grow 1}}
     [ctrls/multi-dropdown {:label         "Tag Filer"
                            :options       options
                            :options-by-id options-by-id
                            :value         (:tag (forms/data form))
                            :on-change     [:forms/change form-id [:tag]]
                            :item-control  item-control}]]))

(defn ^:private search-results [_ results]
  [views.main/pprint results])

(defn ^:private root* [{:keys [form-id form sub:contexts sub:tags] :as attrs}]
  (let [search-event [:resources/submit! [:api.notes/search form-id] (forms/data form)]]
    [:div.flex.layout--space-between
     [views.main/with-resource sub:contexts [context-filter attrs]]
     [views.main/with-resource sub:tags [tag-filter attrs]]
     [:div {:style {:align-self    :end
                    :margin-bottom "16px"}}
      [views.main/plain-button {:on-click (fn [_]
                                            (rf/dispatch search-event))
                                :class    ["is-success"]}
       [views.main/icon :search]
       [:span {:style {:width "8px"}}]
       "Search"]]]))

(defn root [_]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id {:tag #{} :context nil}])))
               sub:form (rf/subscribe [:forms/form form-id])
               sub:contexts (rf/subscribe [:resources/resource :api.contexts/fetch])
               sub:tags (rf/subscribe [:resources/resource :api.tags/fetch])
               sub:notes (rf/subscribe [:resources/resource [:api.notes/search form-id]])]
    (let [form @sub:form]
      [:div.layout--stack-between
       [root* {:form-id      form-id
               :form         form
               :sub:contexts sub:contexts
               :sub:tags     sub:tags}]
       [views.main/with-resource sub:notes [search-results {:hide-init? true}]]])
    (finally
      (rf/dispatch [:resources/destroy [:api.notes/search form-id]])
      (rf/dispatch [:forms/destroy form-id]))))
