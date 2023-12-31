(ns brainard.common.views.components.shared
  (:require
    [brainard.common.utils.maps :as maps]))

(defn plain-button [attrs & content]
  (let [disabled #?(:clj true :default (:disabled attrs))]
    (-> attrs
        (maps/assoc-defaults :type :button)
        (cond-> disabled (update :class (fnil conj []) "is-disabled"))
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
   [:i.fas (update attrs :class conj (str "fa-" (name icon-class)))]))
