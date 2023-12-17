(ns brainard.common.views.pages.search
  "The search page."
  (:require
    [defacto.forms.core :as forms]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.views.pages.shared :as spages]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]))

(def ^:private ^:const form-id ::forms/search)
(def ^:private ^:const search-notes-key [::forms+/valid [::rspecs/notes#select form-id]])

(defn ^:private ->empty-form [{:keys [context] :as query-params} contexts tags]
  (let [data (cond-> {:notes/tags (into #{}
                                        (comp (map keyword) (filter (set tags)))
                                        (colls/wrap-set (:tags query-params)))}
               (contains? contexts context) (assoc :notes/context context))]
    {:data         data
     :pre-commands [[:routing/with-qp! data]]}))

(defn ^:private context-filter [{:keys [*:store form+]} contexts]
  (r/with-let [options (map #(vector % %) contexts)
               options-by-id (into {} options)]
    [ctrls/single-dropdown (-> {:*:store       *:store
                                :label         "Topic filter"
                                :options       options
                                :options-by-id options-by-id}
                               (ctrls/with-attrs form+ [:notes/context]))]))

(defn ^:private tag-filter [{:keys [*:store form+]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [ctrls/multi-dropdown (-> {:*:store       *:store
                               :label         "Tag Filer"
                               :options       options
                               :options-by-id options-by-id}
                              (ctrls/with-attrs form+ [:notes/tags]))]))

(defn ^:private root* [{:keys [*:store] :as attrs} [contexts tags]]
  (store/with-qp-sync-form [sub:form+ {:store        *:store
                                       :resource-key search-notes-key
                                       :init         (:query-params attrs)
                                       :->params     #(->empty-form % contexts tags)}]
    (let [form+ @sub:form+
          form-data (forms/data form+)
          attrs (assoc attrs :form+ form+)]
      [ctrls/form {:*:store      *:store
                   :form+        form+
                   :params       {:pre-commands [[:routing/with-qp! form-data]]}
                   :resource-key search-notes-key
                   :submit/body  [:<>
                                  [comp/icon :search]
                                  [:span.space--left "Search"]]}
       [:div.flex.layout--room-between
        [:div.flex-grow
         [context-filter attrs contexts]]
        [:div.flex-grow
         [tag-filter attrs tags]]]])))

(defn ^:private search-results [route-info [notes]]
  [spages/search-results route-info notes])

(defmethod ipages/page :routes.ui/search
  [{:keys [*:store query-params] :as route-info}]
  (r/with-let [sub:contexts (store/subscribe *:store [::res/?:resource [::rspecs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::rspecs/tags#select]])
               sub:notes (store/subscribe *:store [::res/?:resource search-notes-key])]
    [:div.layout--stack-between
     [comp/with-resources [sub:contexts sub:tags] [root* {:*:store      *:store
                                                          :query-params query-params}]]
     [comp/with-resources [sub:notes] [search-results (assoc route-info :hide-init? true)]]]
    (finally
      (store/emit! *:store [::forms+/destroyed search-notes-key]))))
