(ns brainard.common.views.controls.type-ahead
  "A type-ahead select component which allows new values."
  (:require
    [brainard.api.utils.fns :as fns]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [clojure.string :as string]))

(defn ^:private filter-matches [value items]
  (let [re (re-pattern (string/lower-case (str value)))]
    (filter (fn [item]
              (re-find re (string/lower-case (str item))))
            items)))

(defn ^:private ->type-ahead-key-handler [{:keys [*:state dd-active? matches on-change selected-idx]}]
  (fn [e]
    (when-let [key (#{:key-codes/enter :key-codes/up :key-codes/down}
                    (dom/event->key e))]
      (when dd-active?
        (dom/prevent-default! e)
        (dom/stop-propagation! e)
        (case key
          :key-codes/up (swap! *:state assoc :selected-idx
                               (max 0 (dec (or selected-idx 1))))
          :key-codes/down (swap! *:state assoc :selected-idx
                                 (min (dec (count matches))
                                      (inc (or selected-idx -1))))
          :key-codes/enter (do (swap! *:state assoc
                                      :selected? true
                                      :selected-idx nil)
                               (when selected-idx
                                 (on-change (nth matches selected-idx)))))))))

(defn ^:private type-ahead-trigger [{:keys [*:state on-change] :as attrs}]
  [:div.dropdown-trigger
   [:input.input
    (-> {:ref           (fn [node]
                          (some->> node (swap! *:state assoc :ref)))
         :type          :text
         :auto-complete :off
         :on-change     (fn [e]
                          (swap! *:state assoc :selected? false :selected-idx nil)
                          (on-change (dom/target-value e)))
         :on-key-down   (->type-ahead-key-handler attrs)}
        (merge (select-keys attrs #{:class :disabled :id :ref :value :on-focus :on-blur :auto-focus}))
        (update :on-focus fns/apply-all! (fn [_]
                                           (swap! *:state assoc :focussed? true)))
        (update :on-blur fns/apply-all! (fn [_]
                                          (swap! *:state assoc :focussed? false))))]])

(defn ^:private type-ahead-dd [{:keys [*:state dd-active? matches on-change selected-idx]}]
  [:div.dropdown {:class [(when dd-active? "is-active")]}
   [:div.dropdown-menu {:class [(when dd-active? "is-active")]}
    [:div.dropdown-content
     (for [[idx match] (map-indexed vector matches)]
       ^{:key match}
       [:span.dropdown-item.pointer
        {:class         [(when (= idx selected-idx) "is-active")]
         :tab-index     -1
         :on-mouse-down (fn [_]
                          (swap! *:state assoc :clicking? true))
         :on-mouse-up   (fn [_]
                          (swap! *:state assoc :clicking? false))
         :on-click      (fn [_]
                          (swap! *:state assoc
                                 :selected? true
                                 :selected-idx nil)
                          (on-change match)
                          (dom/focus! (:ref @*:state)))}
        (str match)])]]])

(defn ^:private control* [{:keys [value] :as attrs} [items]]
  (r/with-let [*:state (r/atom {:selected?    false
                                :focussed?    false
                                :selected-idx nil})]
    (let [state @*:state
          matches (filter-matches value items)
          dd-active? (or (:clicking? state)
                         (and (not (:selected? state))
                              (:focussed? state)
                              (>= (count value) 2)
                              (seq matches)))
          sub-attrs (-> attrs
                        (assoc :*:state *:state
                               :matches matches
                               :dd-active? dd-active?
                               :selected-idx (when-let [idx (:selected-idx state)]
                                               (min idx (dec (count matches))))))]
      [:div
       [type-ahead-trigger sub-attrs]
       [type-ahead-dd sub-attrs]])))

(defn control [attrs]
  [comp/with-resources [(:sub:items attrs)] [control* attrs]])
