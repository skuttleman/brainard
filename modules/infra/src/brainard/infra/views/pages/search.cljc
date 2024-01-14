(ns brainard.infra.views.pages.search
  "The search page."
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.reagent :as r]
    [brainard.api.utils.colls :as colls]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
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
    [ctrls/plain-form {:on-submit (fn [_]
                                    (store/dispatch! *:store [:whet.core/with-qp! form-data]))}
     [:div.flex.layout--room-between
      [:div.flex-grow
       [context-filter attrs contexts]]
      [:div.flex-grow
       [tag-filter attrs tags]]]]))

(defn ^:private search-results [route-info [notes]]
  [notes.views/search-results route-info notes])

(defn ^:private root* [{:keys [*:store query-params] :as route-info} [contexts tags]]
  (r/with-let [form-key [::forms+/valid [::specs/notes#select form-id] query-params]
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
  (r/with-let [sub:contexts (store/subscribe *:store [::res/?:resource [::specs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])]
    [comp/with-resources [sub:contexts sub:tags]
     ^{:key (pr-str query-params)}
     [root* route-info]]))
