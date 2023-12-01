(ns brainard.common.views.pages.search
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.navigation.core :as nav]
    [brainard.common.specs :as specs]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.strings :as strings]
    [brainard.common.validations :as valid]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.main :as views.main]))

(defn ^:private ->empty-form [{:keys [context] :as query-params} contexts tags]
  (cond-> {:notes/tags (into #{}
                             (comp (map keyword) (filter tags))
                             (colls/wrap-set (:tags query-params)))}
    (contains? contexts context) (assoc :notes/context context)))

(def ^:private search-validator
  (valid/->validator specs/notes-query))

(defn ^:private init-search-form! [{:keys [form-id query-params]} contexts tags]
  (let [data (->empty-form query-params contexts tags)]
    (rf/dispatch [:forms/create form-id data {:remove-nil? true}])
    (when (nil? (search-validator data))
      (rf/dispatch [:resources/submit! ^:with-qp-sync? [:api.notes/select form-id] data]))
    (rf/subscribe [:forms/form form-id])))

(defn ^:private qp-syncer [{:keys [form-id]} contexts tags]
  (fn [_ _ _ route]
    (let [data (->empty-form (:query-params route) contexts tags)]
      (rf/dispatch [:forms/create form-id data {:remove-nil? true}])
      (if (nil? (search-validator data))
        (rf/dispatch [:resources/submit! [:api.notes/select form-id] data])
        (rf/dispatch [:resources/destroy [:api.notes/select form-id]])))))

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
        [:div.flex.row.layout--space-between
         [:div.flex.row
          [:strong context]
          [:span {:style {:margin-left "8px"}} (strings/truncate-to body 100)]]
         [:a.link {:href  (nav/path-for :routes.ui/note {:notes/id id})
                   :style {:margin-left "8px"}}
          "view"]]
        [:div.flex
         [ctrls/tag-list {:value (take 8 tags)}]
         (when (< 8 (count tags))
           [:em {:style {:margin-left "8px"}} "more..."])]])]))

(defn ^:private root* [{:keys [form-id sub:notes] :as attrs} [contexts tags]]
  (r/with-let [sub:route (doto (rf/subscribe [:routing/route])
                           (add-watch ::qp-sync (qp-syncer attrs contexts tags)))
               sub:form (init-search-form! attrs contexts tags)]
    (let [form @sub:form
          form-data (forms/data form)
          errors (search-validator form-data)
          attrs (assoc attrs :form form :errors errors)]
      [ctrls/form {:form         form
                   :errors       errors
                   :params       form-data
                   :resource-key ^:with-qp-sync? [:api.notes/select form-id]
                   :sub:res      sub:notes
                   :submit/body  [:<>
                                  [views.main/icon :search]
                                  [:span {:style {:margin-left "8px"}}
                                   "Search"]]}
       [:div.flex.layout--space-between
        [context-filter attrs contexts]
        [tag-filter attrs tags]]])
    (finally
      (remove-watch sub:route ::qp-sync))))

(defn root [{:keys [query-params]}]
  (r/with-let [form-id (random-uuid)
               sub:contexts (rf/subscribe [:resources/resource :api.contexts/select])
               sub:tags (rf/subscribe [:resources/resource :api.tags/select])
               sub:notes (rf/subscribe [:resources/resource [:api.notes/select form-id]])]
    [:div.layout--stack-between
     [views.main/with-resources [sub:contexts sub:tags] [root* {:form-id      form-id
                                                                :query-params query-params
                                                                :sub:notes    sub:notes}]]
     [views.main/with-resource sub:notes [search-results {:hide-init? true}]]]
    (finally
      (rf/dispatch [:resources/destroy [:api.notes/select form-id]])
      (rf/dispatch [:forms/destroy form-id]))))
