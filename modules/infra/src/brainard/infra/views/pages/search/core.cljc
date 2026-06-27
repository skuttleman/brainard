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

(defn ^:private ->empty-form [{:keys [archived body context todos] :as query-params} contexts tags]
  (cond-> {:notes/tags     (into #{}
                                 (comp (map keyword) (filter (set tags)))
                                 (colls/wrap-set (:tags query-params)))
           :notes/archived (keyword archived)}
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
   [:incomplete "Incomplete (1+ unfinished)"]
   [:complete "Complete (1+ all finished)"]])

(def ^:private todo-options-by-id
  (into {} todo-options))

(defn ^:private search-form [{:keys [*:store form+] :as attrs} contexts tags]
  (let [form-data (forms/data form+)]
    [ctrls/plain-form {:class       ["search-form"]
                       :on-submit   (fn [_]
                                      (let [bare-params (cond-> (dissoc form-data :notes/archived)
                                                          (empty? (:notes/tags form-data)) (dissoc :notes/tags))
                                            qp (when (seq bare-params)
                                                 form-data)]
                                        (store/dispatch! *:store [::w/with-qp! qp])))
                       :submit/body "Search"}
     [:div.layout--stack-between
      [:div.layout--room-between
       [:div {:style {:flex-basis "27%"}}
        [context-filter attrs contexts]]
       [:div {:style {:flex-basis "27%"}}
        [tag-filter attrs tags]]
       [:div {:style {:flex-basis "27%"}}
        [ctrls/single-dropdown (-> {:*:store        *:store
                                    :attrs->content (comp todo-options-by-id first :value)
                                    :label          "TODO Filter"
                                    :options        (rest todo-options)
                                    :options-by-id  todo-options-by-id}
                                   (ctrls/with-attrs form+ [:notes/todos]))]]
       [:div {:style {:flex-basis "18%"}}
        [ctrls/toggle (-> {:*:store *:store
                           :label   "Include archived?"
                           :on-val  :both
                           :off-val nil}
                          (ctrls/with-attrs form+ [:notes/archived]))]]]
      [ctrls/input (-> {:*:store *:store
                        :label   "Body contents"}
                       (ctrls/with-attrs form+ [:notes/body]))]]]))

(defn ^:private root [*:store {:keys [anchor query-params]} [contexts tags]]
  (store/with-let [form-key [::forms+/valid [::search.act/search] query-params]
                   loaded? (boolean (store/query *:store [::forms/?:form form-key]))
                   sub:form+ (store/form+-sub *:store
                                              form-key
                                              (->empty-form query-params contexts tags))
                   _ (when (and (not loaded?) (seq (dissoc query-params :archived)))
                       (store/dispatch! *:store [::forms+/submit! form-key]))]
    [:div.layout--stack-between
     [search-form {:*:store *:store :form+ @sub:form+} contexts tags]
     [comp/with-resource sub:form+ [notes.views/note-list {:*:store    *:store
                                                           :anchor     anchor
                                                           :anchor?    true
                                                           :hide-init? true}]]]
    (finally
      ;; ??? (do other form+'s actually need this?
      (store/emit! *:store [::res/destroyed (second form-key)]))))

(defmethod ipages/page :routes.ui/search
  [*:store {:keys [query-params] :as route-info}]
  (store/with-let [sub:contexts (store/res-sub *:store ^:static [::specs/contexts#select])
                   sub:tags (store/res-sub *:store ^:static [::specs/tags#select])]
    [comp/with-resources [sub:contexts sub:tags]
     ^{:key (pr-str query-params)}
     [root *:store route-info]]))
