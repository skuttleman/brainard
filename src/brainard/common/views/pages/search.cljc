(ns brainard.common.views.pages.search
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.navigation.core :as nav]
    [brainard.common.specs :as specs]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.validations :as valid]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.utils.strings :as strings]
    [brainard.common.views.main :as views.main]))

(def ^:private empty-form
  {:notes/tags #{}})

(def ^:private search-validator
  (valid/->validator specs/notes-query))

(defn ^:private item-control [item]
  [:span item])

(defn ^:private context-filter [{:keys [errors form sub:notes]} contexts]
  (r/with-let [options (map #(vector % %) contexts)
               options-by-id (into {} options)]
    [:div {:style {:flex-basis "49%"}}
     [ctrls/single-dropdown (-> {:label         "Context filter"
                                 :options       options
                                 :options-by-id options-by-id
                                 :item-control  item-control}
                                (forms/with-attrs form
                                                  sub:notes
                                                  [:notes/context]
                                                  errors))]]))

(defn ^:private tag-filter [{:keys [errors form sub:notes]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [:div {:style {:flex-basis "49%"}}
     [ctrls/multi-dropdown (-> {:label         "Tag Filer"
                                :options       options
                                :options-by-id options-by-id
                                :item-control  item-control}
                               (forms/with-attrs form
                                                 sub:notes
                                                 [:notes/tags]
                                                 errors))]]))

(defn ^:private search-results [_ notes]
  (if-not (seq notes)
    [:span.search-results
     [views.main/alert :info "No search results"]]
    [:ul.search-results
     (for [{:notes/keys [id context body tags]} notes]
       ^{:key id}
       [:li
        [:div.flex.row
         [:strong context]
         [:span {:style {:margin-left "8px"}} (strings/truncate-to body 100)]
         [:a.link {:href  (nav/path-for :routes.ui/note {:notes/id id})
                   :style {:margin-left "8px"}}
          "view"]]
        [:div.flex
         [ctrls/tag-list {:value (take 8 tags)}]
         (when (< 8 (count tags))
           [:em {:style {:margin-left "8px"}} "more..."])]])]))

(defn ^:private root* [{:keys [form-id form sub:contexts sub:notes sub:tags] :as attrs}]
  (let [form-data (forms/data form)
        errors (search-validator form-data)
        attrs (assoc attrs :errors errors)]
    [ctrls/form {:errors       errors
                 :params       form-data
                 :resource-key [:api.notes/select form-id]
                 :sub:res      sub:notes
                 :submit/body  [:<>
                                [views.main/icon :search]
                                [:span {:style {:margin-left "8px"}}
                                 "Search"]]}
     [:div.flex.layout--space-between
      [views.main/with-resource sub:contexts [context-filter attrs]]
      [views.main/with-resource sub:tags [tag-filter attrs]]]]))

(defn root [_]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id empty-form {:remove-nil? true}])))
               sub:form (rf/subscribe [:forms/form form-id])
               sub:contexts (rf/subscribe [:resources/resource :api.contexts/select])
               sub:tags (rf/subscribe [:resources/resource :api.tags/select])
               sub:notes (rf/subscribe [:resources/resource [:api.notes/select form-id]])]
    (let [form @sub:form]
      [:div.layout--stack-between
       [root* {:form-id      form-id
               :form         form
               :sub:contexts sub:contexts
               :sub:tags     sub:tags
               :sub:notes    sub:notes}]
       [views.main/with-resource sub:notes [search-results {:hide-init? true}]]])
    (finally
      (rf/dispatch [:resources/destroy [:api.notes/select form-id]])
      (rf/dispatch [:forms/destroy form-id]))))
