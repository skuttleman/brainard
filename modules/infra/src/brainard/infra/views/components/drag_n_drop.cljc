(ns brainard.infra.views.components.drag-n-drop
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [defacto.forms.core :as forms]
    [defacto.resources.core :as-alias res]))

(defn clear-data! [*:store]
  (doto *:store
    (store/emit! [::forms/changed :brainard/drag-n-drop [::node] nil])
    (store/emit! [::forms/changed :brainard/drag-n-drop [::target] nil])))

(defn ^:private on-drop-fn [*:store {::keys [node target]} e]
  (dom/prevent-default! e)
  (dom/stop-propagation! e)
  (when (and target (not= node target))
    (if (= target {:workspace-nodes/id ::root})
      (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#detach!] node])
      (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#move!]
                                (assoc node :workspace-nodes/new-parent-id (:workspace-nodes/id target))])))
  (clear-data! *:store))

(defn ^:private on-drag-over [*:store {::keys [node]} curr-node e]
  (dom/prevent-default! e)
  (dom/stop-propagation! e)
  (when-not (= curr-node node)
    (store/emit! *:store [::forms/changed :brainard/drag-n-drop [::target] curr-node])))

(defn drop-target [*:store {::keys [node target] :as dnd-state} curr-node & content]
  (let [target? (and node (= curr-node target))]
    (into [:div {:style        (cond-> {:margin-top "24px"}
                                 node (assoc :background-color "#eee"))
                 :class        [(when target? "target")]
                 :on-drag-end  (fn [_] (clear-data! *:store))
                 :on-drag-over (partial on-drag-over *:store dnd-state)
                 :on-drop      (partial on-drop-fn *:store dnd-state)}]
          content)))

(defn drag-n-drop-target [*:store {::keys [node target] :as dnd-state} curr-node & content]
  (let [target? (and (= curr-node target)
                     (not= curr-node node))]
    (into [:div {:draggable    true
                 :style        (when target? {:color            :blue
                                              :background-color "#ddd"})
                 :class        [(when target? "target")]
                 :on-drag-end  (fn [_] (clear-data! *:store))
                 :on-drag-over (partial on-drag-over *:store dnd-state curr-node)
                 :on-drag      #(when-not (= node curr-node)
                                  (store/emit! *:store [::forms/changed :brainard/drag-n-drop [::node] curr-node]))
                 :on-drop      (partial on-drop-fn *:store dnd-state)}]
          content)))

(defn dragging? [dnd-state]
  (some? (::node dnd-state)))
