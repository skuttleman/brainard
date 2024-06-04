(ns brainard.infra.views.controls.dropdown
  "A single/multi select dropdown component."
  (:require
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [clojure.set :as set]
    [whet.utils.reagent :as r]))

(defn ^:private split-selection [{:keys [options value]}]
  (let [{selected true unselected false} (group-by (partial contains? value) options)]
    (concat selected unselected)))

(defn ^:private option-list [{:keys [item-control on-change value] :as attrs
                              :or {item-control (partial vector :span)}}]
  (r/with-let [options (split-selection attrs)]
    [:ul.dropdown-items
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
                       (on-change next-value)
                       (when-let [on-toggle (when (::single? attrs)
                                             (:on-toggle attrs))]
                         (on-toggle e))))}
        [item-control display]])]))

(defn ^:private button-control [{:keys [attrs->content selected] :as attrs}]
  (let [selected-count (count selected)
        content (if attrs->content
                  (attrs->content attrs)
                  (case selected-count
                    0 "Select..."
                    1 "1 Item Selected"
                    (str selected-count " Items Selected")))]
    [comp/plain-button
     (select-keys attrs #{:class :disabled :on-blur :on-click :ref :id})
     [:span.layout--space-after content]
     [comp/icon (if (:open? attrs) :chevron-up :chevron-down)]]))

(defn ^:private dropdown-menu [{:keys [loading? open? options] :as attrs}]
  (when open?
    [:div.dropdown-menu
     [:div.dropdown-content
      [:div.dropdown-body
       (cond
         loading?
         [comp/spinner]

         (seq options)
         [option-list attrs]

         :else
         [comp/alert :info "No results"])]]]))

(defn ^:private dropdown [{:keys [open? options-by-id value] :as attrs}]
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

(defn ^:private control* [attrs attrs']
  [dropdown (merge attrs attrs')])

(defn control [{:keys [options] :as attrs}]
  (let [options-by-id (or (:options-by-id attrs) (into {} options))]
    [comp/openable {:listeners? true} control* (assoc attrs :options-by-id options-by-id)]))

(defn ->single [{:keys [value] :as attrs}]
  (let [value (if (nil? value) #{} #{value})]
    (-> attrs
        (assoc :value value ::single? true)
        (update :on-change (fn [on-change]
                             (fn [values]
                               (let [value-next (first (remove value values))]
                                 (on-change value-next))))))))
