(ns brainard.infra.views.pages.search.core
  "The search page."
  (:require
    [brainard.api.utils.colls :as colls]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.infra.views.pages.search.actions :as search.act]
    [brainard.notes.infra.views :as notes.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [whet.core :as-alias w]
    [whet.utils.reagent :as r]))

(defn ^:private ->empty-form [{:keys [context todos] :as query-params} contexts tags]
  (cond-> {:notes/tags (into #{}
                             (comp (map keyword) (filter (set tags)))
                             (colls/wrap-set (:tags query-params)))}
    (contains? contexts context) (assoc :notes/context context)
    todos (assoc :notes/todos (keyword todos))))

(defn ^:private context-filter [{:keys [*:store form+]} contexts]
  (r/with-let [options (map #(vector % %) contexts)
               options-by-id (into {} options)]
    [ctrls/single-dropdown (-> {:*:store       *:store
                                :label         "Topic Filter"
                                :options       options
                                :options-by-id options-by-id}
                               (ctrls/with-attrs form+ [:notes/context]))]))

(defn ^:private tag-filter [{:keys [*:store form+]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [ctrls/multi-dropdown (-> {:*:store       *:store
                               :label         "Tag Filter"
                               :options       options
                               :options-by-id options-by-id}
                              (ctrls/with-attrs form+ [:notes/tags]))]))

(defn ^:private search-form [{:keys [*:store form+] :as attrs} contexts tags]
  (let [form-data (forms/data form+)]
    [ctrls/plain-form {:on-submit   (fn [_]
                                      (store/dispatch! *:store [::w/with-qp! form-data]))
                       :submit/body "Search"}
     [:div.layout--room-between
      [:div {:style {:flex-basis "40%"}}
       [context-filter attrs contexts]]
      [:div {:style {:flex-basis "40%"}}
       [tag-filter attrs tags]]
      [:div {:style {:flex-basis "20%"}}
       [ctrls/select (-> {:*:store *:store
                          :label   "TODO Filter"}
                         (ctrls/with-attrs form+ [:notes/todos]))
        [[nil "(Unspecified)"]
         [:incomplete "Incomplete (has 1+ unfinished TODOs)"]
         [:complete "Complete (has 1+ TODOs - all finished)"]]]]]]))

(defn ^:private root [*:store {:keys [anchor query-params]} [contexts tags]]
  (r/with-let [form-key [::forms+/valid [::search.act/search] query-params]
               sub:form+ (let [loaded? (boolean (store/query *:store [::forms/?:form form-key]))
                               sub (store/form+-sub *:store
                                                    form-key
                                                    (->empty-form query-params contexts tags))]
                           (when (and (not loaded?) (seq query-params))
                             (store/dispatch! *:store [::forms+/submit! form-key]))
                           sub)]
    [:div.layout--stack-between
     [search-form {:*:store *:store
                   :form+   @sub:form+}
      contexts
      tags]
     [comp/with-resource sub:form+ [notes.views/note-list {:anchor     anchor
                                                           :anchor?    true
                                                           :hide-init? true}]]]
    (finally
      (store/emit! *:store [::forms+/destroyed form-key]))))

(defmethod ipages/page :routes.ui/search
  [*:store {:keys [query-params] :as route-info}]
  (r/with-let [sub:contexts (store/res-sub *:store [::specs/contexts#select])
               sub:tags (store/res-sub *:store [::specs/tags#select])]
    [comp/with-resources [sub:contexts sub:tags]
     ^{:key (pr-str query-params)}
     [root *:store route-info]]))
