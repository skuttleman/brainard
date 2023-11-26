(ns brainard.common.views.controls.type-ahead
  (:require
    [brainard.common.utils.fns :as fns]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [clojure.string :as string]))

(defn ^:private filter-matches [value [status data]]
  (when (= :success status)
    (let [re (re-pattern (string/lower-case (str value)))]
      (filter (comp (partial re-find re) string/lower-case)
              data))))

(defn ^:private ->type-ahead-key-handler [{:keys [comp:state dd-active? matches on-change selected-idx]}]
  (fn [e]
    (when dd-active?
      (when-let [key (#{:key-codes/enter :key-codes/up :key-codes/down}
                      (dom/event->key e))]
        (dom/prevent-default! e)
        (dom/stop-propagation! e)
        (case key
          :key-codes/up (swap! comp:state assoc :selected-idx
                               (max 0 (dec (or selected-idx 1))))
          :key-codes/down (swap! comp:state assoc :selected-idx
                                 (min (dec (count matches))
                                      (inc (or selected-idx -1))))
          :key-codes/enter (when selected-idx
                             (swap! comp:state assoc
                                    :selected? true
                                    :selected-idx nil)
                             (on-change (nth matches selected-idx))))))))

(defn ^:private type-ahead-trigger [{:keys [comp:state on-change] :as attrs}]
  [:div.dropdown-trigger
   [:input.input
    (-> {:type          :text
         :auto-complete :off
         :on-change     (fn [e]
                          (swap! comp:state assoc :selected? false)
                          (on-change (dom/target-value e)))
         :on-key-down   (->type-ahead-key-handler attrs)}
        (merge (select-keys attrs #{:class :disabled :id :ref :value :on-focus :on-blur :auto-focus}))
        (update :on-focus fns/apply-all! (fn [_]
                                           (swap! comp:state assoc :focussed? true)))
        (update :on-blur fns/apply-all! (fn [_]
                                          (swap! comp:state assoc :focussed? false))))]])

(defn ^:private type-ahead-dd [{:keys [comp:state dd-active? matches on-change selected-idx]}]
  [:div.dropdown {:class [(when dd-active? "is-active")]}
   [:div.dropdown-menu {:class [(when dd-active? "is-active")]}
    [:div.dropdown-content
     (for [[idx match] (map-indexed vector matches)]
       ^{:key match}
       [:a.dropdown-item {:href      "#"
                          :class     [(when (= idx selected-idx) "is-active")]
                          :tab-index -1
                          :on-click  (fn [_]
                                       (swap! comp:state assoc
                                              :selected? true
                                              :selected-idx nil)
                                       (on-change match))}
        match])]]])

(defn control [_attrs]
  (let [comp:state (r/atom {:selected?    false
                            :focussed?    false
                            :selected-idx nil})]
    (fn [{:keys [sub:items value] :as attrs}]
      (let [state @comp:state
            matches (filter-matches value @sub:items)
            sub-attrs (-> attrs
                          (assoc :comp:state comp:state
                                 :matches matches
                                 :dd-active? (and (not (:selected? state))
                                                  (:focussed? state)
                                                  (>= (count value) 2)
                                                  (seq matches))
                                 :selected-idx (when-let [idx (:selected-idx state)]
                                                 (min idx (dec (count matches))))))]
        [:div.control
         [type-ahead-trigger sub-attrs]
         [type-ahead-dd sub-attrs]]))))
