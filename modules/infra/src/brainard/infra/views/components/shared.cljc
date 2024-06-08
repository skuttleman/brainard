(ns brainard.infra.views.components.shared
  (:require
    [brainard.api.utils.maps :as maps]
    [brainard.infra.stubs.dom :as dom]
    [whet.utils.reagent :as r]))

(defn plain-button [attrs & content]
  (let [disabled #?(:clj true :default (:disabled attrs))]
    (-> attrs
        (assoc :disabled disabled)
        (maps/assoc-defaults :type :button)
        (cond-> disabled (update :class (fnil conj []) "is-disabled"))
        (select-keys #{:auto-focus :id :disabled :on-click :style :class :ref :type})
        (->> (conj [:button.button]))
        (into content))))

(defn tile [heading body & tabs]
  [:div.tile
   [:div.panel {:style {:min-width        "400px"
                        :background-color "#fcfcfc"}}
    (when heading
      [:div.panel-heading
       heading])
    (when (seq tabs)
      (into [:div.panel-tabs
             {:style {:padding         "8px"
                      :justify-content :flex-start}}]
            tabs))
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
