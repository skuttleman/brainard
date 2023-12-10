(ns brainard.common.views.pages.search
  "The search page."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.validations.core :as valid]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.views.pages.shared :as spages]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]))

(def ^:private ^:const form-id ::forms/search)

(defn ^:private ->empty-form [{:keys [context] :as query-params} contexts tags]
  (let [data (cond-> {:notes/tags (into #{}
                                        (comp (map keyword) (filter (set tags)))
                                        (colls/wrap-set (:tags query-params)))}
               (contains? contexts context) (assoc :notes/context context))]
    {:data         data
     :pre-commands [[:routing/with-qp! data]]}))

(def ^:private search-validator
  (valid/->validator valid/notes-query))

(defn ^:private context-filter [{:keys [*:store errors form sub:notes]} contexts]
  (r/with-let [options (map #(vector % %) contexts)
               options-by-id (into {} options)]
    [ctrls/single-dropdown (-> {:*:store       *:store
                                :label         "Context filter"
                                :options       options
                                :options-by-id options-by-id}
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
                               :options-by-id options-by-id}
                              (ctrls/with-attrs form
                                                sub:notes
                                                [:notes/tags]
                                                errors))]))



(defn ^:private root* [{:keys [*:store sub:notes] :as attrs} [contexts tags]]
  (store/with-qp-sync-form [sub:form {:form-id      form-id
                                      :store        *:store
                                      :init         (:query-params attrs)
                                      :resource-key [::rspecs/notes#select form-id]
                                      :->params     #(->empty-form % contexts tags)
                                      :validator    search-validator}]
    (let [form @sub:form
          form-data (forms/data form)
          errors (search-validator form-data)
          attrs (assoc attrs :form form :errors errors)]
      [ctrls/form {:*:store      *:store
                   :form         form
                   :errors       errors
                   :params       {:data         form-data
                                  :pre-commands [[:routing/with-qp! form-data]]}
                   :resource-key [::rspecs/notes#select form-id]
                   :sub:res      sub:notes
                   :submit/body  [:<>
                                  [comp/icon :search]
                                  [:span.space--left "Search"]]}
       [:div.flex.layout--room-between
        [:div.flex-grow
         [context-filter attrs contexts]]
        [:div.flex-grow
         [tag-filter attrs tags]]]])))

(defmethod ipages/page :routes.ui/search
  [{:keys [*:store query-params]}]
  (r/with-let [sub:contexts (store/subscribe *:store [:resources/?:resource ::rspecs/contexts#select])
               sub:tags (store/subscribe *:store [:resources/?:resource ::rspecs/tags#select])
               sub:notes (store/subscribe *:store [:resources/?:resource [::rspecs/notes#select form-id]])]
    [:div.layout--stack-between
     [comp/with-resources [sub:contexts sub:tags] [root* {:*:store      *:store
                                                          :query-params query-params
                                                          :sub:notes    sub:notes}]]
     [comp/with-resources [sub:notes] [spages/search-results {:hide-init? true}]]]
    (finally
      (store/emit! *:store [:resources/destroyed [::rspecs/notes#select form-id]])
      (store/emit! *:store [:forms/destroyed form-id]))))