(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(declare tree-list)

(defn ^:private ->form-id [node-id]
  [::forms+/std [::specs/local ::specs/workspace#create! node-id]])

(def ^:private ^:const submit-params
  {:ok-commands [[::res/submit! [::specs/local ::specs/workspace#fetch]]]})

(defn ^:private ->on-submit [*:store close-form form-id]
  (fn [_]
    (store/dispatch! *:store [::forms+/submit! form-id submit-params])
    (close-form)))

(defn ^:private new-node-form [*:store close-form node-id]
  (r/with-let [form-id (->form-id node-id)
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! form-id
                                                       (when node-id {:workspace-nodes/parent-id node-id})])
                             (store/subscribe *:store [::forms+/?:form+ form-id]))
               on-submit (->on-submit *:store close-form form-id)]
    (let [form+ @sub:form+]
      [ctrls/plain-form {:form+           form+
                         :inline-buttons? true
                         :on-submit       on-submit}
       [ctrls/input (ctrls/with-attrs {:*:store *:store} form+ [:workspace-nodes/data])]])
    (finally
      (store/emit! *:store [::forms+/recreated form-id]))))

(defn ^:private new-node-li [*:store node-id]
  (r/with-let [*:open? (r/atom false)
               on-change (partial reset! *:open?)]
    (let [open? @*:open?]
      [:div.flex.layout--room-between
       {:style (when-not open? {:margin-top    "-2px"
                                :margin-bottom "-16px"})}
       [comp/plain-toggle {:class     ["is-white" "is-small"]
                           :value     open?
                           :on-change on-change}]
       (when open?
         ^{:key (or node-id "new")}
         [new-node-form *:store on-change node-id])])))

(defn ^:private tree-node [*:store {:workspace-nodes/keys [id data nodes]}]
  [:div (cond-> {:style {:margin-left "8px"}}
          (empty? nodes) (assoc :class ["flex" "row"]))
   [:p data]
   [tree-list *:store nodes id]])

(defn ^:private tree-list [*:store nodes node-id]
  [:ul.tree-list.layout--stack-between
   {:class [(when (seq nodes) "bullets")]}
   (for [node (sort-by :workspace-nodes/id nodes)]
     ^{:key (:workspace-nodes/id node)}
     [:li [tree-node *:store node]])
   ^{:key (or node-id "new")}
   [:li [new-node-li *:store node-id]]])

(defn ^:private root [*:store [root-nodes]]
  [:div
   [:h2.subtitle "Welcome to your workspace"]
   [tree-list *:store root-nodes nil]])

(defmethod ipages/page :routes.ui/workspace
  [{:keys [*:store]}]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))]
    [comp/with-resources [sub:data] [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/local ::specs/workspace#fetch]]))))
