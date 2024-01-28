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

(defn ^:private ->on-submit [*:store close-form form-id]
  (fn [_]
    (store/dispatch! *:store [::forms+/submit! form-id])
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
       [ctrls/input (-> {:*:store     *:store
                         :auto-focus? true}
                        (ctrls/with-attrs form+ [:workspace-nodes/data]))]])
    (finally
      (store/emit! *:store [::forms+/recreated form-id]))))

(defn ^:private new-node-li [*:store node-id]
  (r/with-let [*:open? (r/atom false)
               on-change (partial reset! *:open?)]
    (let [open? @*:open?]
      [:div.flex.layout--room-between
       {:style (when-not open? {:margin-top    "-2px"
                                :margin-bottom "-16px"})}
       [comp/plain-toggle {:class     ["form-toggle" "is-white" "is-small"]
                           :on-change on-change
                           :value     open?}]
       (when open?
         ^{:key (or node-id "new")}
         [new-node-form *:store on-change node-id])])))

(defn ^:private tree-node [*:store dnd-state {:workspace-nodes/keys [id nodes] :as node}]
  (r/with-let [modal [:modals/sure?
                      {:description  "Delete this sub tree?"
                       :yes-commands [[::res/submit! [::specs/local ::specs/workspace#delete!] node]]}]]
    [:div (cond-> {:style {:margin-left "8px"}}
            (empty? nodes) (assoc :class ["flex" "row"]))
     [:div.flex.row
      [comp/drag-n-drop-target *:store dnd-state node (:workspace-nodes/data node)]
      [comp/plain-button {:class    ["is-white" "is-small"]
                          :on-click (fn [_]
                                      (store/dispatch! *:store [:modals/create! modal]))}
       [comp/icon {:class ["is-danger"]} :trash]]]
     [tree-list *:store dnd-state nodes id]]))

(defn ^:private tree-list [*:store dnd-state nodes node-id]
  [:ul.tree-list.layout--stack-between
   {:class [(when (seq nodes) "bullets")]}
   (doall (for [node (sort-by :workspace-nodes/index nodes)]
            ^{:key (:workspace-nodes/id node)}
            [:li [tree-node *:store dnd-state node]]))
   ^{:key (or node-id "new")}
   [:li [new-node-li *:store node-id]]])

(defn ^:private root [*:store dnd-state [root-nodes]]
  [:div.tree-root
   [:h2.subtitle "Welcome to your workspace"]
   [comp/drop-target *:store dnd-state {:workspace-nodes/id ::root}
    [tree-list *:store dnd-state root-nodes nil]]])

(defmethod ipages/page :routes.ui/workspace
  [*:store _]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))
               sub:dnd (store/subscribe *:store [::forms/?:form :brainard/drag-n-drop])]
    [comp/with-resources [sub:data] [root *:store (forms/data @sub:dnd)]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/local ::specs/workspace#fetch]]))))
