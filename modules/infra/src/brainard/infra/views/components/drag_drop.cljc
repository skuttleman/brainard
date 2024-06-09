(ns brainard.infra.views.components.drag-drop
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.edn :as edn]
    [defacto.forms.core :as forms]
    [whet.utils.reagent :as r]))

(declare node-list drag-node-list)

(defn ^:private ->mouse-up [*:store sub:form on-drop]
  (fn [_]
    (let [form @sub:form
          form-id (forms/id form)
          {::keys [drag-id target-id]} (forms/data form)]
      (when (and drag-id target-id)
        (on-drop drag-id target-id))
      (store/emit! *:store [::forms/changed
                            form-id
                            [::drag-id]
                            nil]))))

(defn ^:private ->mouse-down [*:store form node-id]
  (fn [e]
    (dom/stop-propagation! e)
    (store/emit! *:store [::forms/changed (forms/id form) [::drag-id] node-id])))

(defn ^:private ->mouse-move [*:store *:pos sub:form]
  (fn [e]
    (let [form @sub:form]
      (reset! *:pos {:x (.-clientX e)
                     :y (.-clientY e)})
      (when-let [target (-> e .-target (.getAttribute "data-target"))]
        (when (not= target (pr-str (::target-id (forms/data form))))
          (store/emit! *:store [::forms/changed
                                (forms/id form)
                                [::target-id]
                                (edn/read-string target)]))))))

(defn ^:private ->mouse-leave [*:store form-id]
  (fn [_]
    (store/emit! *:store [::forms/changed form-id [::target-id] nil])))

(defn ^:private drag-node-view [{:keys [*:pos comp sub:form] :as attrs} node]
  (let [{::keys [drag-id]} (forms/data @sub:form)
        dragging? (= (:id node) drag-id)]
    [:div {:style (when dragging?
                    (let [{:keys [x y]} @*:pos]
                      {:position :fixed
                       :top      y
                       :left     (+ 8 x)}))}
     (conj comp {:type (if dragging? :dragged :within-drag)} node)
     (when-let [children (seq (:children node))]
       [drag-node-list attrs children])]))

(defn ^:private drag-node-list [attrs nodes]
  [:ul.node-list
   (for [{node-id :id :as node} nodes]
     ^{:key (str [:at node-id])}
     [:li.node-item
      [drag-node-view attrs node]])])

(defn ^:private node-view [{:keys [*:store comp sub:form] :as attrs} {node-id :id :as node}]
  (let [form @sub:form
        {::keys [drag-id target-id]} (forms/data form)
        target? (= target-id [:at node-id])
        children (:children node)]
    [:div {:on-mouse-down (->mouse-down *:store form node-id)}
     [:div (cond-> {}
             (and drag-id target?) (assoc :class ["drop-target"])
             (empty? children) (assoc :data-target (pr-str [:at node-id])))
      (conj comp
            {:type (cond
                     (and drag-id target?) :target
                     drag-id :dynamic
                     :else :static)}
            node)]
     (when (seq children)
       [node-list attrs (:children node)])]))

(defn ^:private node-list [{:keys [sub:form] :as attrs} nodes]
  (let [{::keys [drag-id target-id]} (forms/data @sub:form)]
    [:ul.node-list
     (for [[idx {node-id :id :keys [parent-id] :as node}] (map-indexed vector nodes)
           :let [dragging? (= node-id drag-id)]
           [pos :as target] (cond-> [[:at node-id]]
                              (and drag-id (not dragging?))
                              (conj [:after parent-id node-id])

                              (and drag-id (not dragging?) (zero? idx))
                              (->> (cons [:front parent-id])))
           :let [node? (= :at pos)
                 target? (= target target-id)]]
       ^{:key (str target)}
       [:li {:class [(when node? "node-item")]}
        (cond
          dragging? [drag-node-view attrs node]
          node? [node-view attrs node]
          :else [:div {:class       [(when (and drag-id target?) "drop-target")]
                       :style       {:height "4px" :width "100%"}
                       :data-target (pr-str target)}])])]))

(defn control [{:keys [*:store id on-drop] :as attrs} nodes]
  (r/with-let [form-id [::form id]
               sub:form (-> *:store
                            (store/emit! [::forms/created form-id])
                            (store/subscribe [::forms/?:form form-id]))
               *:pos (r/atom {:x 0 :y 0})
               mouse-move (dom/add-listener! dom/window :mousemove (->mouse-move *:store *:pos sub:form))
               mouse-up (dom/add-listener! dom/window :mouseup (->mouse-up *:store sub:form on-drop))]
    (let [{::keys [drag-id]} (forms/data @sub:form)]
      [:div.drag-n-drop {:on-mouse-leave (when drag-id
                                           (->mouse-leave *:store form-id))}
       [node-list (assoc attrs :sub:form sub:form :*:pos *:pos) nodes]])
    (finally
      (dom/remove-listener! mouse-move)
      (dom/remove-listener! mouse-up)
      (store/emit! *:store [::forms/destroyed form-id]))))
