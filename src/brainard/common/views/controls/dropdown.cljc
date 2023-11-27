(ns brainard.common.views.controls.dropdown
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.main :as views.main]
    [clojure.set :as set]))

(defn option-list [{:keys [item-control on-change options value]}]
  (r/with-let [[selected unselected] (reduce (fn [[sel unsel] [val :as option]]
                                               (if (contains? value val)
                                                 [(conj sel option) unsel]
                                                 [sel (conj unsel option)]))
                                             [[] []]
                                             options)
               options (concat selected unselected)]
    [:ul.dropdown-items.lazy-list
     (for [[id display] options
           :let [selected? (contains? value id)]]
       ^{:key id}
       [:li.dropdown-item.pointer
        {:class    [(when selected? "is-active")]
         :on-click (comp (fn [_]
                           (let [next-value (if (contains? value id)
                                              (disj value id)
                                              ((fnil conj #{}) value id))]
                             (on-change next-value)))
                         dom/stop-propagation!)}
        [item-control display]])]))

(defn button [{:keys [attrs->content selected] :as attrs}]
  (let [selected-count (count selected)
        content (if attrs->content
                  (attrs->content attrs)
                  (case selected-count
                    0 "Select…"
                    1 "1 Item Selected"
                    (str selected-count " Items Selected")))]
    [views.main/plain-button
     (select-keys attrs #{:class :disabled :on-blur :on-click :ref})
     content
     [:span
      {:style {:margin-left "10px"}}
      [views.main/icon (if (:open? attrs) :chevron-up :chevron-down)]]]))

(defn ^:private dropdown* [attrs]
  (let [{:keys [button-control loading? list-control open? options options-by-id value]
         :or   {list-control option-list button-control button}} attrs
        selected (seq (map options-by-id value))]
    [:div.dropdown
     {:class [(when open? "is-active")]}
     [:div.dropdown-trigger
      [button-control
       (-> attrs
           (set/rename-keys {:on-toggle :on-click})
           (cond->
             selected (assoc :selected selected)
             open? (update :class conj "is-focused")))]]
     (when open?
       [:div.dropdown-menu
        [:div.dropdown-content
         [:div.dropdown-body
          (cond
            loading?
            [views.main/spinner]

            (seq options)
            [list-control attrs]

            :else
            [views.main/alert :info "No results"])]]])]))

(defn ^:private openable-dropdown [attrs attrs']
  [dropdown* (merge attrs attrs')])

(defn control [{:keys [options] :as attrs}]
  (let [options-by-id (or (:options-by-id attrs) (into {} options))]
    [views.main/openable openable-dropdown (assoc attrs :options-by-id options-by-id)]))

(defn singleable [{:keys [value] :as attrs}]
  (let [value (if (nil? value) #{} #{value})]
    (-> attrs
        (assoc :value value)
        (update :on-change (fn [on-change]
                             (fn [values]
                               (let [value-next (first (remove value values))]
                                 (on-change value-next))))))))
