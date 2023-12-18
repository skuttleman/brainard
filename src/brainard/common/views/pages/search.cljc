(ns brainard.common.views.pages.search
  "The search page."
  (:require
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [brainard.common.views.pages.shared :as spages]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]))

(def ^:private ^:const form-id ::forms/search)

(defn ^:private ->empty-form [{:keys [context] :as query-params} contexts tags]
  (cond-> {:notes/tags (into #{}
                             (comp (map keyword) (filter (set tags)))
                             (colls/wrap-set (:tags query-params)))}
    (contains? contexts context) (assoc :notes/context context)))

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

(defn ^:private search-form [{:keys [*:store form+] :as attrs} contexts tags]
  (let [form-data (forms/data form+)]
    [ctrls/plain-form {:form+     form+
                       :on-submit (fn [_]
                                    (store/dispatch! *:store [:routing/with-qp! form-data]))}
     [:div.flex.layout--room-between
      [:div.flex-grow
       [context-filter attrs contexts]]
      [:div.flex-grow
       [tag-filter attrs tags]]]]))

(defn ^:private search-results [route-info [notes]]
  [spages/search-results route-info notes])

(defn ^:private root* [{:keys [*:store query-params] :as route-info} [contexts tags]]
  (r/with-let [form-key [::forms+/valid [::rspecs/notes#select form-id] query-params]
               sub:form+ (let [loaded? (boolean (store/query *:store [::forms/?:form form-key]))]
                           (store/emit! *:store [::forms/created form-key
                                                 (->empty-form query-params contexts tags)])
                           (when-not loaded? (store/dispatch! *:store [::forms+/submit! form-key]))
                           (store/subscribe *:store [::forms+/?:form+ form-key]))]
    [:div.layout--stack-between
     [search-form {:*:store      *:store
                   :form+        @sub:form+
                   :query-params query-params}
      contexts
      tags]
     [comp/with-resources [sub:form+] [search-results (assoc route-info :hide-init? true)]]]
    (finally
      (store/emit! *:store [::forms+/destroyed form-key]))))

(defmethod ipages/page :routes.ui/search
  [{:keys [*:store query-params] :as route-info}]
  (r/with-let [sub:contexts (store/subscribe *:store [::res/?:resource [::rspecs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::rspecs/tags#select]])]
    [comp/with-resources [sub:contexts sub:tags]
     ^{:key (pr-str query-params)}
     [root* route-info]]))
