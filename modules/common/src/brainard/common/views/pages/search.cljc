(ns brainard.common.views.pages.search
  "The search page."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.validations.core :as valid]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.routing :as rte]
    [brainard.common.utils.strings :as strings]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]))

(defn ^:private ->empty-form [{:keys [context] :as query-params} contexts tags]
  (cond-> {:notes/tags (into #{}
                             (comp (map keyword) (filter (set tags)))
                             (colls/wrap-set (:tags query-params)))}
    (contains? contexts context) (assoc :notes/context context)))

(def ^:private search-validator
  (valid/->validator valid/notes-query))

(defn ^:private init-search-form! [{:keys [*:store form-id query-params]} contexts tags]
  (let [data (->empty-form query-params contexts tags)]
    (store/dispatch! *:store [:forms/ensure! form-id data {:remove-nil? true}])
    (store/subscribe *:store [:forms/?form form-id])))

(defn ^:private qp-syncer [{:keys [*:store form-id]} contexts tags]
  (fn [_ _ _ {:keys [query-params]}]
    (let [data (->empty-form query-params contexts tags)]
      (or
        (do (store/dispatch! *:store [:forms/created form-id data {:remove-nil? true}])
            (when (nil? (search-validator data))
              (store/dispatch! *:store [:resources/submit! ^:with-qp-sync? [:api.notes/select! form-id] data])
              true)))
      (store/dispatch! *:store [:resources/destroyed [:api.notes/select! form-id]]))))

(defn ^:private item-control [item]
  [:span item])

(defn ^:private context-filter [{:keys [*:store errors form sub:notes]} contexts]
  (r/with-let [options (map #(vector % %) contexts)
               options-by-id (into {} options)]
    [:div {:style {:flex-basis "49%"}}
     [ctrls/single-dropdown (-> {:*:store       *:store
                                 :label         "Context filter"
                                 :options       options
                                 :options-by-id options-by-id
                                 :item-control  item-control}
                                (forms/with-attrs form
                                                  sub:notes
                                                  [:notes/context]
                                                  errors))]]))

(defn ^:private tag-filter [{:keys [*:store errors form sub:notes]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [:div {:style {:flex-basis "49%"}}
     [ctrls/multi-dropdown (-> {:*:store       *:store
                                :label         "Tag Filer"
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
     [comp/alert :info "No search results"]]
    [:ul.search-results
     (for [{:notes/keys [id context body tags]} notes]
       ^{:key id}
       [:li
        [:div.layout--space-between
         [:div.layout--row
          [:strong context]
          [:span {:style {:margin-left "8px"}} (strings/truncate-to body 100)]]
         [:a.link {:href  (rte/path-for :routes.ui/note {:notes/id id})
                   :style {:margin-left "8px"}}
          "view"]]
        [:div.flex
         [comp/tag-list {:value (take 8 tags)}]
         (when (< 8 (count tags))
           [:em {:style {:margin-left "8px"}} "more..."])]])]))

(defn ^:private root* [{:keys [*:store form-id sub:notes] :as attrs} [contexts tags]]
  (r/with-let [sub:route (doto (store/subscribe *:store [:routing/?route])
                           (add-watch ::qp-sync (qp-syncer attrs contexts tags)))
               sub:form (init-search-form! attrs contexts tags)]
    (let [form @sub:form
          form-data (forms/data form)
          errors (search-validator form-data)
          attrs (assoc attrs :form form :errors errors)]
      [ctrls/form {:*:store      *:store
                   :form         form
                   :errors       errors
                   :params       form-data
                   :resource-key ^:with-qp-sync? [:api.notes/select! form-id]
                   :sub:res      sub:notes
                   :submit/body  [:<>
                                  [comp/icon :search]
                                  [:span {:style {:margin-left "8px"}}
                                   "Search"]]}
       [:div.layout--space-between
        [context-filter attrs contexts]
        [tag-filter attrs tags]]])
    (finally
      (remove-watch sub:route ::qp-sync))))

(defmethod ipages/page :routes.ui/search
  [{:keys [*:store query-params]}]
  (r/with-let [form-id ::forms/search
               sub:contexts (store/subscribe *:store [:resources/?resource :api.contexts/select!])
               sub:tags (store/subscribe *:store [:resources/?resource :api.tags/select!])
               sub:notes (store/subscribe *:store [:resources/?resource [:api.notes/select! form-id]])]
    [:div.layout--stack-between
     [comp/with-resources [sub:contexts sub:tags] [root* {:*:store      *:store
                                                          :form-id      form-id
                                                          :query-params query-params
                                                          :sub:notes    sub:notes}]]
     [comp/with-resource sub:notes [search-results {:hide-init? true}]]]
    (finally
      (store/dispatch! *:store [:resources/destroyed [:api.notes/select! form-id]])
      (store/dispatch! *:store [:forms/destroyed form-id]))))
