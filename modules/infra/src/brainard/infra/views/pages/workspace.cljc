(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

;; TODO - component-ize dragon-drop
(defn ^:private on-drop-fn [*:store *:dnd-state]
  (let [{::keys [node target]} @*:dnd-state]
    (when (and target (not= node target))
      (if (= target {:workspace-nodes/id ::root})
        (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#detach!] node])
        (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#move!]
                                  (assoc node :workspace-nodes/new-parent-id (:workspace-nodes/id target))])))
    (swap! *:dnd-state dissoc ::node ::target)))

(defn ^:private on-drag-fn [*:dnd-state node]
  (swap! *:dnd-state assoc ::node node))

(defn ^:private drag-n-drop [*:store *:dnd-state node node->content]
  (let [dnd-state @*:dnd-state
        target? (and (= node (::target dnd-state))
                     (not= node (::node dnd-state)))]
    [:div {:draggable     true
           :style         (when target? {:color :blue})
           :class         [(when target? "target")]
           :on-drag-end   #(swap! *:dnd-state dissoc ::target)
           :on-drag-over  (fn [e]
                            (dom/prevent-default! e)
                            (when-not (= node (::node dnd-state))
                              (swap! *:dnd-state assoc ::target node)))
           :on-drag-leave #(swap! *:dnd-state dissoc ::node)
           :on-drag       (partial on-drag-fn *:dnd-state node)
           :on-drop       (partial on-drop-fn *:store *:dnd-state)}
     (node->content node)]))







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

(defn ^:private tree-node [*:store *:dnd-state {:workspace-nodes/keys [id nodes] :as node}]
  (r/with-let [modal [:modals/sure?
                      {:description  "Delete this sub tree?"
                       :yes-commands [[::res/submit! [::specs/local ::specs/workspace#delete!] node]]}]]
    [:div (cond-> {:style {:margin-left "8px"}}
            (empty? nodes) (assoc :class ["flex" "row"]))
     [:div.flex.row
      [drag-n-drop *:store *:dnd-state node :workspace-nodes/data]
      [comp/plain-button {:class    ["is-white" "is-small"]
                          :on-click (fn [_]
                                      (store/dispatch! *:store [:modals/create! modal]))}
       [comp/icon {:class ["is-danger"]} :trash]]]
     [tree-list *:store *:dnd-state nodes id]]))

(defn ^:private tree-list [*:store *:dnd-state nodes node-id]
  [:ul.tree-list.layout--stack-between
   {:class [(when (seq nodes) "bullets")]}
   (for [node (sort-by :workspace-nodes/id nodes)]
     ^{:key (:workspace-nodes/id node)}
     [:li [tree-node *:store *:dnd-state node]])
   ^{:key (or node-id "new")}
   [:li [new-node-li *:store node-id]]])

(defn ^:private root [*:store [root-nodes]]
  (r/with-let [*:dnd-state (r/atom nil)]
    (let [{::keys [target node]} @*:dnd-state
          target? (= {:workspace-nodes/id ::root} target)]
      [:div.tree-root
       [:h2.subtitle "Welcome to your workspace"]
       [tree-list *:store *:dnd-state root-nodes nil]
       (when node
         [:div {:style        {:margin-top "24px"}
                :class        [(when target? "target")]
                :on-drag-end  #(swap! *:dnd-state dissoc ::target)
                :on-drag-over (fn [e]
                                (dom/prevent-default! e)
                                (swap! *:dnd-state assoc ::target {:workspace-nodes/id ::root}))
                :on-drop      (partial on-drop-fn *:store *:dnd-state)}
          "top level"])])))

(defmethod ipages/page :routes.ui/workspace
  [{:keys [*:store]}]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))]
    [comp/with-resources [sub:data] [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/local ::specs/workspace#fetch]]))))
