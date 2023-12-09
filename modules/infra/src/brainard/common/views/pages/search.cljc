(ns brainard.common.views.pages.search
  "The search page."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.validations.core :as valid]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]))

(def ^:private ^:const form-id ::forms/search)

(defn ^:private ->empty-form [{:keys [context] :as query-params} contexts tags]
  (cond-> {:notes/tags (into #{}
                             (comp (map keyword) (filter (set tags)))
                             (colls/wrap-set (:tags query-params)))}
    (contains? contexts context) (assoc :notes/context context)))

(def ^:private search-validator
  (valid/->validator valid/notes-query))

(defn ^:private init-search-form! [{:keys [*:store query-params]} contexts tags]
  (let [data (->empty-form query-params contexts tags)]
    (store/dispatch! *:store [:forms/ensure! form-id data {:remove-nil? true}])
    (when (nil? (search-validator data))
      (store/dispatch! *:store [:resources/ensure! [::rspecs/notes#select form-id] data]))
    (store/subscribe *:store [:forms/?:form form-id])))

(defn ^:private qp-syncer [{:keys [*:store]} contexts tags]
  (fn [_ _ _ {:keys [query-params]}]
    (let [data (->empty-form query-params contexts tags)]
      (when-not (= data (forms/data (store/query *:store [:forms/?:form form-id])))
        (or (do (store/emit! *:store [:forms/created form-id data {:remove-nil? true}])
                (when (nil? (search-validator data))
                  (store/dispatch! *:store [:resources/submit! [::rspecs/notes#select form-id] data])
                  true))
            (store/emit! *:store [:resources/destroyed [::rspecs/notes#select form-id]]))))))

(defn ^:private item-control [item]
  [:span item])

(defn ^:private context-filter [{:keys [*:store errors form sub:notes]} contexts]
  (r/with-let [options (map #(vector % %) contexts)
               options-by-id (into {} options)]
    [ctrls/single-dropdown (-> {:*:store       *:store
                                :label         "Context filter"
                                :options       options
                                :options-by-id options-by-id
                                :item-control  item-control}
                               (ctrls/with-attrs form
                                                 sub:notes
                                                 [:notes/context]
                                                 errors))]))

(defn ^:private tag-filter [{:keys [*:store errors form sub:notes]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [ctrls/multi-dropdown (-> {:*:store       *:store
                               :label         "Tag Filer"
                               :options       options
                               :options-by-id options-by-id
                               :item-control  item-control}
                              (ctrls/with-attrs form
                                                sub:notes
                                                [:notes/tags]
                                                errors))]))

(defn ^:private search-results [_ [notes]]
  (if-not (seq notes)
    [:span.search-results
     [comp/alert :info "No search results"]]
    [:ul.search-results
     (for [{:notes/keys [id context body tags]} notes]
       ^{:key id}
       [:li
        [:div.layout--row
         [:strong.layout--no-shrink context]
         [:span.flex-grow.space--left.truncate
          body]
         [:a.link.space--left {:href (rte/path-for :routes.ui/note {:notes/id id})}
          "view"]]
        [:div.flex
         [comp/tag-list {:value (take 8 tags)}]
         (when (< 8 (count tags))
           [:em.space--left "more..."])]])]))

(defn ^:private root* [{:keys [*:store sub:notes] :as attrs} [contexts tags]]
  (r/with-let [sub:route (doto (store/subscribe *:store [:routing/?:route])
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
                   :resource-key [::rspecs/notes#select form-id]
                   :sub:res      sub:notes
                   :submit/body  [:<>
                                  [comp/icon :search]
                                  [:span.space--left "Search"]]}
       [:div.flex.layout--room-between
        [:div.flex-grow
         [context-filter attrs contexts]]
        [:div.flex-grow
         [tag-filter attrs tags]]]])
    (finally
      (remove-watch sub:route ::qp-sync))))

(defmethod ipages/page :routes.ui/search
  [{:keys [*:store query-params]}]
  (r/with-let [sub:contexts (store/subscribe *:store [:resources/?:resource ::rspecs/contexts#select])
               sub:tags (store/subscribe *:store [:resources/?:resource ::rspecs/tags#select])
               sub:notes (store/subscribe *:store [:resources/?:resource [::rspecs/notes#select form-id]])]
    [:div.layout--stack-between
     [comp/with-resources [sub:contexts sub:tags] [root* {:*:store      *:store
                                                          :query-params query-params
                                                          :sub:notes    sub:notes}]]
     [comp/with-resources [sub:notes] [search-results {:hide-init? true}]]]
    (finally
      (store/emit! *:store [:resources/destroyed [::rspecs/notes#select form-id]])
      (store/emit! *:store [:forms/destroyed form-id]))))
