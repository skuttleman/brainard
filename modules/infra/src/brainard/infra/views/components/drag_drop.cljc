(ns brainard.infra.views.components.drag-drop
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.edn :as edn]
    [defacto.forms.core :as forms]
    [whet.utils.reagent :as r]))

(declare node-list drag-node-list)

(defn ^:private drag-node-view [*:store form node]
  (let [{::keys [drag-id mouse]} (forms/data form)
        dragging? (= (:id node) drag-id)]
    [:div {:style (when dragging?
                    {:position :fixed
                     :top      (:y mouse)
                     :left     (+ 8 (:x mouse))})}
     [:p (:content node)]
     (when-let [children (seq (:children node))]
       [drag-node-list *:store form children])]))

(defn ^:private drag-node-list [*:store form nodes]
  [:ul.node-list
   (for [{node-id :id :as node} nodes]
     ^{:key (str [:at node-id])}
     [:li.node-item
      [drag-node-view *:store form node]])])

(defn ^:private node-view [*:store form {node-id :id :as node}]
  (let [{::keys [drag-id target-id]} (forms/data form)
        target? (= target-id [:at node-id])
        children (:children node)]
    [:div {:on-mouse-down (fn [e]
                            (dom/stop-propagation! e)
                            (store/emit! *:store [::forms/changed
                                                  (forms/id form)
                                                  [::drag-id]
                                                  node-id]))}
     [:div (cond-> {}
             (and drag-id target?) (assoc :class ["drop-target"])
             (empty? children) (assoc :data-target (pr-str [:at node-id])))
      (:content node)]
     (when (seq children)
       [node-list *:store form (:children node)])]))

(defn ^:private node-list [*:store form nodes]
  (let [{::keys [drag-id target-id]} (forms/data form)]
    [:ul.node-list
     (for [[idx {node-id :id :as node}] (map-indexed vector nodes)
           :let [dragging? (= node-id drag-id)]
           [pos :as target] (cond-> [[:at node-id]]
                              (and drag-id (not dragging?))
                              (conj [:after node-id])

                              (and drag-id (not dragging?) (zero? idx))
                              (->> (cons [:before node-id])))
           :let [node? (= :at pos)
                 target? (= target target-id)]]
       ^{:key (str target)}
       [:li {:class [(when node? "node-item")]}
        (cond
          dragging? [drag-node-view *:store form node]
          node? [node-view *:store form node]
          :else [:div {:class       [(when (and drag-id target?) "drop-target")]
                       :style       {:height "4px" :width "100%"}
                       :data-target (pr-str target)}])])]))

(defn control [{:keys [*:store id on-drop]} nodes]
  (r/with-let [form-id [::form id]
               sub:form (-> *:store
                            (store/emit! [::forms/created form-id])
                            (store/subscribe [::forms/?:form form-id]))
               mouse-up (dom/add-listener! dom/window
                                           :mouseup
                                           (fn [_]
                                             (let [{::keys [drag-id target-id]} (forms/data @sub:form)]
                                               (when (and drag-id target-id)
                                                 (on-drop (first target-id) drag-id (second target-id)))
                                               (store/emit! *:store [::forms/changed
                                                                     form-id
                                                                     [::drag-id]
                                                                     nil]))))
               mouse-move (dom/add-listener! dom/window
                                             :mousemove
                                             (fn [e]
                                               (store/emit! *:store [::forms/changed
                                                                     form-id
                                                                     [::mouse]
                                                                     {:x (.-clientX e)
                                                                      :y (.-clientY e)}])
                                               (when-let [target (-> e .-target (.getAttribute "data-target"))]
                                                 (when (not= target (pr-str (::target-id (forms/data @sub:form))))
                                                   (store/emit! *:store [::forms/changed
                                                                         form-id
                                                                         [::target-id]
                                                                         (edn/read-string target)])))))]
    (let [form @sub:form
          {::keys [drag-id]} (forms/data form)]
      [:div.drag-n-drop {:on-mouse-leave (fn [_]
                                           (when drag-id
                                             (store/emit! *:store [::forms/changed
                                                                   (forms/id form)
                                                                   [::target-id]
                                                                   nil])))}
       [node-list *:store form nodes]])
    (finally
      (dom/remove-listener! mouse-move)
      (dom/remove-listener! mouse-up)
      (store/emit! *:store [::forms/destroyed form-id]))))
