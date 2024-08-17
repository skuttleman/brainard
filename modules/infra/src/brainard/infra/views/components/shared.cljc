(ns brainard.infra.views.components.shared
  (:require
    [brainard.api.utils.maps :as maps]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.routing :as rte]
    [whet.utils.navigation :as nav]
    [whet.utils.reagent :as r]))

(defn plain-button [{:keys [*:store commands events on-click] :as attrs} & content]
  (let [disabled #?(:clj true :default (:disabled attrs))
        on-click (or on-click
                     (fn [_]
                       (as-> *:store $
                             (reduce store/dispatch! $ commands)
                             (reduce store/emit! $ events))))]
    (-> attrs
        (assoc :disabled disabled :on-click on-click)
        (maps/assoc-defaults :type :button)
        (cond-> disabled (update :class (fnil conj []) "is-disabled"))
        (select-keys #{:auto-focus :id :disabled :on-click :style :class :ref :type :tab-index})
        (->> (conj [:button.button]))
        (into content))))

(defn link [{:keys [token route-params query-params] :as attrs} & content]
  (let [href (when token
               (nav/path-for rte/all-routes token route-params query-params))]
    (into [:a.link (-> attrs
                       (cond-> href (assoc :href href))
                       (select-keys #{:href :class :target}))]
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
