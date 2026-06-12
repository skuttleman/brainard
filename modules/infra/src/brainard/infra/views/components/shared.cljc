(ns brainard.infra.views.components.shared
  "Reusable components."
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.stubs.dom :as dom]
   [brainard.infra.utils.routing :as rte]
   [slag.utils.maps :as maps]
   [whet.utils.navigation :as nav]
   [whet.utils.reagent :as r]))

(defn handler-with-store [cb {:keys [*:store commands events]}]
  (fn [e]
    (when cb
      (cb e))
    (when *:store
      (as-> *:store $
            (reduce store/emit! $ events)
            (reduce store/dispatch! $ commands)))))

(defn plain-button [attrs & content]
  (let [disabled #?(:clj true :default (:disabled attrs))
        attrs (-> attrs
                  (select-keys #{:auto-focus :id :on-blur :on-click :style :class :ref :tab-index :type})
                  (assoc :disabled disabled)
                  (update :on-click handler-with-store attrs)
                  (maps/assoc-defaults :type :button)
                  (cond-> disabled (update :class (fnil conj []) "is-disabled")))]
    (into [:button.button attrs] content)))

(defn checkbox [{:keys [value] :as attrs}]
  (let [disabled #?(:clj true :default (:disabled attrs))
        attrs (-> attrs
                  (select-keys #{:auto-focus :id :on-blur :on-change :style :class :ref :tab-index :value})
                  (assoc :disabled disabled :type :checkbox :checked (boolean value))
                  (update :on-change handler-with-store attrs)
                  (cond-> disabled (update :class (fnil conj []) "is-disabled")))]
    [:input.checkbox attrs]))

(defn link [{:keys [token route-params query-params] :as attrs} & content]
  (let [href (when token
               (nav/path-for rte/all-routes token route-params query-params))]
    (into [:a.link (-> attrs
                       (cond-> href (assoc :href href))
                       (select-keys #{:href :class :target :download}))]
          content)))

(defn tile [heading body & tabs]
  [:div.tile
   [:div.panel
    (when heading
      [:div.panel-heading heading])
    (when (seq tabs)
      (into [:div.panel-tabs] tabs))
    [:div.panel-block.block
     body]]])

(defn icon
  ([icon-class]
   (icon {} icon-class))
  ([attrs icon-class]
   (let [attrs (-> attrs
                   (select-keys #{:class :style})
                   (update :class conj (str "lni-" (name icon-class))))]
     [:i.lni attrs])))

(defn with-auto-focus [component]
  (fn [{:keys [auto-focus?]} & _]
    (let [vnode (volatile! nil)
          ref (fn [node] (some->> node (vreset! vnode)))]
      (r/create-class
       {:component-did-mount
        (fn [this]
          (when-let [node @vnode]
            (let [attrs (second (r/argv this))]
              (when (and auto-focus? (not (:disabled attrs)))
                (vreset! vnode nil)
                (dom/focus! node)))))
        :reagent-render
        (fn [attrs & args]
          (into [component (cond-> attrs
                             auto-focus? (assoc :ref ref :auto-focus true))]
                args))}))))
