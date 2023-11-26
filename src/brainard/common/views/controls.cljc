(ns brainard.common.views.controls
  (:require
    [brainard.common.utils.fns :as fns]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.common :as views.common]
    [clojure.pprint :as pp]
    [clojure.string :as string]))

(defn ^:private with-id [component]
  (fn [_attrs & _args]
    (let [id (gensym "form-field")]
      (fn [attrs & args]
        (into [component (assoc attrs :id id)] args)))))

(defn ^:private dispatch-on-change [on-change]
  (fn [value]
    (when on-change
      (rf/dispatch-sync (conj on-change value)))))

(defn ^:private with-trim-blur [component]
  (fn [attrs & args]
    (-> attrs
        (update :on-blur (fn [on-blur]
                           (fn [e]
                             (when-let [on-change (some-> (:on-change attrs) dispatch-on-change)]
                               (on-change (some-> attrs :value string/trim not-empty)))
                             (when on-blur
                               (on-blur e)))))
        (->> (conj [component]))
        (into args))))

(defn form-field [{:keys [errors form-field-class id label label-small? warnings]} & body]
  (let [errors (seq (remove nil? errors))]
    [:div.form-field
     {:class (into [(cond errors "errors" warnings "warnings")] form-field-class)}
     [:<>
      (when label
        [:label.label
         (cond-> {:html-for id}
           label-small? (assoc :style {:font-weight :normal
                                       :font-size   "0.8em"}))
         label])
      (into [:div.form-field-control] body)]
     (when errors
       [:ul.error-list
        (for [error errors]
          [:li.error
           {:key error}
           error])])
     (when warnings
       [:ul.warning-list
        (for [warning warnings]
          [:li.warning
           {:key warning}
           warning])])]))

(def ^{:arglists '([attrs options])} select
  (with-id
    (fn [{:keys [disabled on-change value] :as attrs} options]
      (let [option-values (set (map first options))
            value (if (contains? option-values value)
                    value
                    ::empty)]
        [form-field
         attrs
         [:select.select
          (-> {:value     (str value)
               :disabled  disabled
               :on-change (comp (dispatch-on-change on-change)
                                (into {} (map (juxt str identity) option-values))
                                dom/target-value)}
              (merge (select-keys attrs #{:class :id :on-blur :ref})))
          (for [[option label attrs] (cond->> options
                                       (= ::empty value) (cons [::empty
                                                                "Choose…"
                                                                {:disabled true}]))
                :let [str-option (str option)]]
            [:option
             (assoc attrs :value str-option :key str-option :selected (= option value))
             label])]]))))

(def ^{:arglists '([attrs])} textarea
  (with-id
    (with-trim-blur
      (fn [{:keys [disabled on-change value] :as attrs}]
        [form-field
         attrs
         [:textarea.textarea
          (-> {:value     value
               :disabled  disabled
               :on-change (comp (dispatch-on-change on-change) dom/target-value)}
              (merge (select-keys attrs #{:class :id :on-blur :ref})))]]))))

(def ^{:arglists '([attrs])} input
  (with-id
    (with-trim-blur
      (fn [{:keys [disabled on-change type] :as attrs}]
        [form-field
         attrs
         [:input.input
          (-> {:type      (or type :text)
               :disabled  disabled
               :on-change (comp (dispatch-on-change on-change) dom/target-value)}
              (merge (select-keys attrs #{:class :id :on-blur :ref :value :on-focus :auto-focus})))]]))))

(defn ^:private filter-matches [value [status data]]
  (when (= :success status)
    (let [re (re-pattern (string/lower-case (str value)))]
      (filter (comp (partial re-find re) string/lower-case)
              data))))

(defn ^:private type-ahead-trigger [{:keys [comp:state dd-active? matches on-change selected-idx]
                                     :as     attrs}]
  [:div.dropdown-trigger
   [:input.input
    (-> {:type          :text
         :auto-complete :off
         :on-change     (fn [e]
                          (swap! comp:state assoc :selected? false)
                          (on-change (dom/target-value e)))
         :on-key-down   (fn [e]
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
                                                   (on-change (nth matches selected-idx)))))))}
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
       ^{:key match} [:a.dropdown-item {:href      "#"
                                        :on-click  (fn [_]
                                                     (swap! comp:state assoc
                                                            :selected? true
                                                            :selected-idx nil)
                                                     (on-change match))
                                        :class     [(when (= idx selected-idx) "is-active")]
                                        :tab-index -1}
                      match])]]])

(def ^{:arglists '([attrs])} type-ahead
  (with-id
    (with-trim-blur
      (fn [_attrs]
        (let [comp:state (r/atom {:selected? false
                                  :focussed? false
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
                                                       (min idx (dec (count matches)))))
                                (update :on-change dispatch-on-change))]
              [form-field
               attrs
               [:div.control
                [type-ahead-trigger sub-attrs]
                [type-ahead-dd sub-attrs]]])))))))

(def ^{:arglists '([attrs])} checkbox
  (with-id
    (fn [{:keys [disabled on-change value] :as attrs}]
      [form-field
       attrs
       [:input.checkbox
        (-> {:checked   (boolean value)
             :type      :checkbox
             :disabled  disabled
             :on-change (fn [_]
                          (rf/dispatch-sync (conj on-change (not value))))}
            (merge (select-keys attrs #{:class :id :on-blur :ref})))]])))

(defn form [{:keys [ready? valid? buttons disabled on-submit] :as attrs} & fields]
  (let [disabled (or disabled (not ready?) (not valid?))]
    (-> [:form.form.layout--stack-between
         (merge {:on-submit (fn [e]
                              (dom/prevent-default! e)
                              (rf/dispatch on-submit))}
                (select-keys attrs #{:class :style}))]
        (into fields)
        (conj (cond-> [:div.buttons
                       [views.common/plain-button
                        {:class    ["is-primary" "submit"]
                         :type     :submit
                         :disabled disabled}
                        (:submit/text attrs "Submit")]]
                (not ready?)
                (conj [:div {:style {:margin-bottom "8px"}} [views.common/spinner]])

                buttons
                (into buttons))))))
