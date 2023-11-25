(ns brainard.common.views.controls
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.views.common :as views.common]
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

(defn form-field [{:keys [attempted? errors form-field-class id label label-small?]} & body]
  (let [errors (seq (remove nil? errors))
        show-errors? (and errors attempted?)]
    [:div.form-field
     {:class (into [(when show-errors? "errors")] form-field-class)}
     [:<>
      (when label
        [:label.label
         (cond-> {:html-for id}
           label-small? (assoc :style {:font-weight :normal
                                       :font-size   "0.8em"}))
         label])
      (into [:div.form-field-control] body)]
     (when show-errors?
       [:ul.error-list
        (for [error errors]
          [:li.error
           {:key error}
           error])])]))

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
