(ns brainard.common.views.controls.dropdown
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [clojure.set :as set]))

(defn ^:private split-selection [{:keys [options value]}]
  (let [{selected true unselected false} (group-by (partial contains? value) options)]
    (concat selected unselected)))

(defn ^:private option-list [{:keys [item-control on-change value] :as attrs}]
  (r/with-let [options (split-selection attrs)]
    [:ul.dropdown-items
     {:style    {:max-height "400px"
                 :overflow-y :scroll}}
     (for [[id display] options
           :let [selected? (contains? value id)]]
       ^{:key id}
       [:li.dropdown-item.pointer
        {:class    [(when selected? "is-active")]
         :on-click (fn [e]
                     (dom/stop-propagation! e)
                     (let [next-value (if (contains? value id)
                                        (disj value id)
                                        ((fnil conj #{}) value id))]
                       (on-change next-value)))}
        [item-control display]])]))

(defn ^:private button [{:keys [attrs->content selected] :as attrs}]
  (let [selected-count (count selected)
        content (if attrs->content
                  (attrs->content attrs)
                  (case selected-count
                    0 "Select…"
                    1 "1 Item Selected"
                    (str selected-count " Items Selected")))]
    [comp/plain-button
     (select-keys attrs #{:class :disabled :on-blur :on-click :ref})
     content
     [:span
      {:style {:margin-left "10px"}}
      [comp/icon (if (:open? attrs) :chevron-up :chevron-down)]]]))

(defn ^:private dropdown-menu [{:keys [loading? list-control open? options]
                                :or   {list-control option-list} :as attrs}]
  (when open?
    [:div.dropdown-menu
     [:div.dropdown-content
      [:div.dropdown-body
       (cond
         loading?
         [comp/spinner]

         (seq options)
         [list-control attrs]

         :else
         [comp/alert :info "No results"])]]]))

(defn ^:private dropdown* [{:keys [button-control open? options-by-id value]
                            :or   {button-control button} :as attrs}]
  (let [selected (seq (map options-by-id value))]
    [:div.dropdown
     {:class [(when open? "is-active")]}
     [:div.dropdown-trigger
      [button-control
       (-> attrs
           (set/rename-keys {:on-toggle :on-click})
           (cond->
             selected (assoc :selected selected)
             open? (update :class conj "is-focused")))]]
     [dropdown-menu attrs]]))

(defn ^:private openable-dropdown [attrs attrs']
  [dropdown* (merge attrs attrs')])

(defn control [{:keys [options] :as attrs}]
  (let [options-by-id (or (:options-by-id attrs) (into {} options))]
    [comp/openable openable-dropdown (assoc attrs :options-by-id options-by-id)]))

(defn singleable [{:keys [value] :as attrs}]
  (let [value (if (nil? value) #{} #{value})]
    (-> attrs
        (assoc :value value)
        (update :on-change (fn [on-change]
                             (fn [values]
                               (let [value-next (first (remove value values))]
                                 (on-change value-next))))))))
