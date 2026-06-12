(ns brainard.infra.views.pages.search.core
  "The search page."
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.store.specs :as-alias specs]
   [brainard.infra.views.components.core :as comp]
   [brainard.infra.views.controls.core :as ctrls]
   [brainard.infra.views.pages.interfaces :as ipages]
   [brainard.infra.views.pages.search.actions :as search.act]
   [brainard.notes.infra.views :as notes.views]
   [defacto.forms.core :as forms]
   [defacto.forms.plus :as-alias forms+]
   [defacto.resources.core :as-alias res]
   [slag.utils.colls :as colls]
   [whet.core :as-alias w]
   [whet.utils.reagent :as r]))

(defn ^:private ->empty-form [{:keys [body context todos] :as query-params} contexts tags]
  (cond-> {:notes/tags (into #{}
                             (comp (map keyword) (filter (set tags)))
                             (colls/wrap-set (:tags query-params)))}
    (contains? contexts context) (assoc :notes/context context)
    todos (assoc :notes/todos (keyword todos))
    body (assoc :notes/body body)))

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

(def ^:private todo-options
  [[nil "(Unspecified)"]
   [:incomplete "Incomplete (has 1+ unfinished TODOs)"]
   [:complete "Complete (has 1+ TODOs - all finished)"]])

(def ^:private todo-options-by-id
  (into {} todo-options))

(defn ^:private search-form [{:keys [*:store form+] :as attrs} contexts tags]
  (let [form-data (forms/data form+)]
    [ctrls/plain-form {:class       ["search-form"]
                       :on-submit   (fn [_]
                                      (store/dispatch! *:store [::w/with-qp! form-data]))
                       :submit/body "Search"}
     [:div.layout--stack-between
      [:div.layout--room-between
       [:div {:style {:flex-basis "34%"}}
        [context-filter attrs contexts]]
       [:div {:style {:flex-basis "32%"}}
        [tag-filter attrs tags]]
       [:div {:style {:flex-basis "34%"}}
        [ctrls/single-dropdown (-> {:*:store        *:store
                                    :attrs->content (comp todo-options-by-id first :value)
                                    :label          "TODO Filter"
                                    :options        (rest todo-options)
                                    :options-by-id  todo-options-by-id}
                                   (ctrls/with-attrs form+ [:notes/todos]))]]]
      [ctrls/input (-> {:*:store *:store
                        :label   "Body contents"}
                       (ctrls/with-attrs form+ [:notes/body]))]]]))

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
     [search-form {:*:store *:store :form+ @sub:form+} contexts tags]
     [comp/with-resource sub:form+ [notes.views/note-list {:anchor     anchor
                                                           :anchor?    true
                                                           :hide-init? true}]]]
    (finally
      (-> *:store
          (store/emit! [::forms+/destroyed form-key])
          (store/emit! [::res/destroyed (second form-key)])))))

(defmethod ipages/page :routes.ui/search
  [*:store {:keys [query-params] :as route-info}]
  (r/with-let [sub:contexts (store/res-sub *:store [::specs/contexts#select])
               sub:tags (store/res-sub *:store [::specs/tags#select])]
    [comp/with-resources [sub:contexts sub:tags]
     ^{:key (pr-str query-params)}
     [root *:store route-info]]))
