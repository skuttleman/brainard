(ns brainard.common.views.controls.core
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.utils.fns :as fns]
    [brainard.common.views.controls.type-ahead :as type-ahead]
    [brainard.common.views.controls.tags-editor :as tags-editor]
    [brainard.common.views.common :as views.common]
    [clojure.string :as string]))

(defn ^:private dispatch-on-change [on-change]
  (fn [value]
    (when on-change
      (rf/dispatch-sync (conj on-change value)))))

(defn ^:private with-dispatch-on-change [component]
  (fn [attrs & args]
    (into [component (update attrs :on-change dispatch-on-change)] args)))

(defn ^:private with-id [component]
  (fn [_attrs & _args]
    (let [id (gensym "form-field")]
      (fn [attrs & args]
        (into [component (assoc attrs :id id)] args)))))

(defn ^:private with-trim-blur [component]
  (fn [attrs & args]
    (-> attrs
        (update :on-blur fns/apply-all!
                (fn [_]
                  (when-let [on-change (:on-change attrs)]
                    (on-change (some-> attrs :value string/trim not-empty)))))
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
    (with-dispatch-on-change
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
                 :on-change (comp on-change
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
               label])]])))))

(def ^{:arglists '([attrs])} textarea
  (with-id
    (with-dispatch-on-change
      (with-trim-blur
        (fn [{:keys [disabled on-change value] :as attrs}]
          [form-field
           attrs
           [:textarea.textarea
            (-> {:value     value
                 :disabled  disabled
                 :on-change (comp on-change dom/target-value)}
                (merge (select-keys attrs #{:class :id :on-blur :ref})))]])))))

(def ^{:arglists '([attrs])} input
  (with-id
    (with-dispatch-on-change
      (with-trim-blur
        (fn [{:keys [disabled on-change type] :as attrs}]
          [form-field
           attrs
           [:input.input
            (-> {:type      (or type :text)
                 :disabled  disabled
                 :on-change (comp on-change dom/target-value)}
                (merge (select-keys attrs #{:class :id :on-blur :ref :value :on-focus :auto-focus})))]])))))

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

(def ^{:arglists '([attrs])} tags-editor
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         attrs
         [tags-editor/control attrs]]))))

(def ^{:arglists '([attrs])} type-ahead
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         attrs
         [type-ahead/control attrs]]))))

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
