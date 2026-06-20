(ns brainard.infra.views.controls.type-ahead
  "A type-ahead select component which allows new values."
  (:require
   [brainard.infra.stubs.dom :as dom]
   [brainard.infra.views.components.core :as comp]
   [clojure.string :as string]
   [defacto.resources.core :as res]
   [slag.utils.fns :as fns]
   [whet.utils.reagent :as r]))

(defn ^:private ->pattern [s]
  (->> s
       (map (fn [c]
              (-> (case c
                    (\$ \^ \( \) \[ \] \{ \} \. \?) (str "\\" c)
                    (str c))
                  string/lower-case)))
       string/join
       re-pattern))

(defn ^:private filter-matches [value items]
  (let [re (->pattern value)]
    (filter (fn [item]
              (re-find re (string/lower-case (str item))))
            items)))

(defn ^:private ->type-ahead-key-handler [{:keys [*:state dd-active? dd-disabled? matches on-add on-select selected-idx]}]
  (fn [e]
    (when-let [key (#{:key-codes/enter :key-codes/up :key-codes/down :key-codes/tab}
                    (dom/event->key e))]
      (cond
        (and dd-active? (not dd-disabled?))
        (do
          (dom/prevent-default! e)
          (dom/stop-propagation! e)
          (case key
            :key-codes/up (swap! *:state assoc :selected-idx
                                 (max 0 (dec (or selected-idx 1))))
            :key-codes/down (swap! *:state assoc :selected-idx
                                   (min (dec (count matches))
                                        (inc (or selected-idx -1))))
            (:key-codes/enter :key-codes/tab) (do (swap! *:state assoc
                                                         :selected? true
                                                         :selected-idx nil)
                                                  (when selected-idx
                                                    (on-select (nth matches selected-idx))))))

        (and on-add (= :key-codes/enter key))
        (on-add e)))))

(defn ^:private trigger [{:keys [*:state dd-disabled?] :as attrs}]
  [:div.dropdown-trigger
   [comp/plain-input
    (-> attrs
        (assoc :auto-complete :off
               :ref (fn [node]
                      (some->> node (swap! *:state assoc :ref)))
               :on-key-down (->type-ahead-key-handler attrs))
        (update :on-change fns/apply-all (fn [_]
                                           (when-not dd-disabled?
                                             (swap! *:state assoc
                                                    :selected? false
                                                    :selected-idx nil))))
        (update :on-focus fns/apply-all (fn [_]
                                          (when-not dd-disabled?
                                            (swap! *:state assoc :focussed? true))))
        (update :on-blur fns/apply-all (fn [_]
                                         (when-not dd-disabled?
                                           (swap! *:state assoc :focussed? false)))))]])

(defn ^:private drop-down [{:keys [*:state dd-active? dd-disabled? item-fn key-fn matches on-select selected-idx]}]
  (let [classes [(when dd-active? "is-active")
                 (when dd-disabled? "is-disabled")]]
    [:div.dropdown {:class classes}
     [:div.dropdown-menu {:class classes}
      (when (seq matches)
        [:ul.dropdown-content
         (for [[idx match] (map-indexed vector matches)]
           ^{:key (key-fn match)}
           [:li.dropdown-item
            {:class         [(when (= idx selected-idx) "is-active")
                             (when-not dd-disabled? "pointer")]
             :tab-index     -1
             :on-mouse-down (fn [_]
                              (when-not dd-disabled?
                                (swap! *:state assoc :clicking? true)))
             :on-mouse-up   (fn [_]
                              (swap! *:state assoc :clicking? false))
             :on-click      (fn [_]
                              (when-not dd-disabled?
                                (swap! *:state assoc
                                       :selected? true
                                       :selected-idx nil)
                                (on-select match)
                                (dom/focus! (:ref @*:state))))}
            (item-fn match)])])]]))

(defn ^:private control [{:keys [on-change on-select value] :as attrs} matches]
  (r/with-let [*:state (r/atom {:selected?    false
                                :focussed?    false
                                :selected-idx nil})]
    (let [state @*:state
          dd-active? (or (:clicking? state)
                         (and (not (:selected? state))
                              (:focussed? state)
                              (>= (count value) 2)
                              (seq matches)))
          sub-attrs (-> attrs
                        (assoc :*:state *:state
                               :matches matches
                               :dd-active? dd-active?
                               :on-select (or on-select on-change)
                               :selected-idx (when-let [idx (:selected-idx state)]
                                               (min idx (dec (count matches))))))]
      [:div.type-ahead {:style {:height "40px"}}
       [trigger sub-attrs]
       [drop-down sub-attrs]])))

(defn ^:private autocomplete* [{:keys [value] :as attrs} items]
  (let [matches (filter-matches value items)]
    [control (assoc attrs :key-fn identity :item-fn str) matches]))

(defn autocomplete [{:keys [sub:items] :as attrs}]
  [comp/with-resource sub:items [autocomplete* attrs]])

(defn typeahead [{:keys [sub:items remove-fn] :as attrs}]
  (let [res @sub:items
        matches (cond->> (res/payload res)
                  remove-fn (remove remove-fn))
        requesting? (res/requesting? res)]
    [control (assoc attrs :dd-disabled? requesting?) (when-not requesting? matches)]))
