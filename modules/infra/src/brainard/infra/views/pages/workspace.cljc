(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [clojure.pprint :as pp]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

;; TODO - component-ize dragon-drop
(defn ^:private on-drag-end [*:store _]
  (store/emit! *:store [::forms/changed ::form [:node] nil])
  (store/emit! *:store [::forms/changed ::form [:target] nil]))

(defn ^:private on-drop-fn [*:store sub:dnd e]
  (let [{:keys [node target]} (forms/data @sub:dnd)]
    (dom/prevent-default! e)
    (dom/stop-propagation! e)
    (when (and target (not= node target))
      (if (= target {:workspace-nodes/id ::root})
        (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#detach!] node])
        (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#move!]
                                  (assoc node :workspace-nodes/new-parent-id (:workspace-nodes/id target))])))
    (on-drag-end *:store e)))

(defn ^:private on-drag-over [*:store sub:dnd curr-node e]
  (let [dnd-state (forms/data @sub:dnd)]
    (dom/prevent-default! e)
    (dom/stop-propagation! e)
    (when-not (= curr-node (:node dnd-state))
      (store/emit! *:store [::forms/changed ::form [:target] curr-node]))))

(defn ^:private drag-n-drop [*:store sub:dnd curr-node & content]
  (let [dnd-state (forms/data @sub:dnd)
        target? (and (= curr-node (:target dnd-state))
                     (not= curr-node (:node dnd-state)))]
    (into [:div {:draggable    true
                 :style        (when target? {:color            :blue
                                              :background-color "#ddd"})
                 :class        [(when target? "target")]
                 :on-drag-end  (partial on-drag-end *:store)
                 :on-drag-over (partial on-drag-over *:store sub:dnd curr-node)
                 :on-drag      #(when-not (= (:node dnd-state) curr-node)
                                  (store/emit! *:store [::forms/changed ::form [:node] curr-node]))
                 :on-drop      (partial on-drop-fn *:store sub:dnd)}]
          content)))

(defn ^:private drop-target [*:store sub:dnd curr-node & content]
  (let [{:keys [target]} (forms/data @sub:dnd)
        target? (= curr-node target)]
    (into [:div {:style        {:margin-top       "24px"
                                :background-color "#eee"}
                 :class        [(when target? "target")]
                 :on-drag-end  (partial on-drag-end *:store)
                 :on-drag-over (partial on-drag-over *:store sub:dnd)
                 :on-drop      (partial on-drop-fn *:store sub:dnd)}]
          content)))






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

(defn ^:private tree-node [*:store sub:dnd {:workspace-nodes/keys [id nodes] :as node}]
  (r/with-let [modal [:modals/sure?
                      {:description  "Delete this sub tree?"
                       :yes-commands [[::res/submit! [::specs/local ::specs/workspace#delete!] node]]}]]
    [:div (cond-> {:style {:margin-left "8px"}}
            (empty? nodes) (assoc :class ["flex" "row"]))
     [:div.flex.row
      [drag-n-drop *:store sub:dnd node (:workspace-nodes/data node)]
      [comp/plain-button {:class    ["is-white" "is-small"]
                          :on-click (fn [_]
                                      (store/dispatch! *:store [:modals/create! modal]))}
       [comp/icon {:class ["is-danger"]} :trash]]]
     [tree-list *:store sub:dnd nodes id]]))

(defn ^:private tree-list [*:store sub:dnd nodes node-id]
  (let [dnd-data (forms/data @sub:dnd)]
    [:ul.tree-list.layout--stack-between
     {:class [(when (seq nodes) "bullets")]}
     (when (:node dnd-data)
       [drop-target *:store sub:dnd {:workspace-nodes/id ::root}
        "before"])
     (for [node (sort-by :workspace-nodes/index nodes)]
       ^{:key (:workspace-nodes/id node)}
       [:li [tree-node *:store sub:dnd node]
        (when (:node dnd-data)
          [drop-target *:store sub:dnd {:workspace-nodes/id ::root}
           "after"])])
     ^{:key (or node-id "new")}
     [:li [new-node-li *:store node-id]]]))

(defn ^:private root [*:store [root-nodes]]
  (r/with-let [sub:dnd (do (store/emit! *:store [::forms/created ::form])
                            (store/subscribe *:store [::forms/?:form ::form]))]
    [:div.tree-root
     [:h2.subtitle "Welcome to your workspace"]
     (conj (if (:node (forms/data @sub:dnd))
             [drop-target *:store sub:dnd {:workspace-nodes/id ::root}]
             [:div])
           [tree-list *:store sub:dnd root-nodes nil])]))

(defmethod ipages/page :routes.ui/workspace
  [{:keys [*:store]}]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))]
    [comp/with-resources [sub:data] [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/local ::specs/workspace#fetch]]))))
