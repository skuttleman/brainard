(ns brainard.infra.views.controls.core
  "All controls in this namespace take a store event vector as on-change. The changed value
   will be [[conj]]'d onto the event. The following will call (dispatch [:my-event target-value])

   [input {:on-change [:my-event]}]"
  (:require
    [brainard.api.utils.fns :as fns]
    [brainard.api.utils.maps :as maps]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.dropdown :as dd]
    [brainard.infra.views.controls.shared :as shared]
    [brainard.infra.views.controls.tags-editor :as tags-editor]
    [brainard.infra.views.controls.type-ahead :as type-ahead]
    [clojure.string :as string]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defn ^:private disabled-compat [disabled]
  #?(:clj true :default disabled))

(defn ^:private emit-on-change [on-change *:store]
  (fn [value]
    (when on-change
      (store/emit! *:store (conj on-change value)))))

(defn ^:private with-emit-on-change [component]
  (fn [{:keys [*:store] :as attrs} & args]
    (into [component (update attrs :on-change emit-on-change *:store)] args)))

(defn ^:private with-id [component]
  (fn [attrs & args]
    (r/with-let [id (gensym "form-field")]
      (into [component (assoc attrs :id id)] args))))

(defn ^:private with-disabled-compat [component]
  (fn [attrs & args]
    (-> [component (update attrs :disabled disabled-compat)]
        (into args))))

(defn ^:private with-trim-blur [component]
  (fn [attrs & args]
    (-> attrs
        (update :on-blur fns/apply-all
                (fn [_]
                  (when-let [on-change (:on-change attrs)]
                    (on-change (some-> attrs :value string/trim not-empty)))))
        (->> (conj [component]))
        (into args))))

(defn ^:private form-field-label [{:keys [id label label-small?]}]
  (when label
    [:label.label
     (cond-> {:html-for id}
       label-small? (assoc :class ["small"]))
     label]))

(defn ^:private form-field-meta-list
  ([type items]
   (form-field-meta-list type items false))
  ([type items form?]
   (when (seq items)
     [:ul {:class [(str (when form? "form-")
                        (name type)
                        "-list")]}
      (for [item items]
        ^{:key item}
        [:li {:class [(name type)]}
         item])])))

(defn ^:private form-field [{:keys [changed? errors form-field-class warnings] :as attrs} & body]
  (let [errors (seq (remove nil? errors))]
    [:div.form-field
     {:class (into [(when errors "errors")
                    (when warnings "warnings")
                    (when changed? "is-changed")]
                   form-field-class)}
     [:<>
      [form-field-label attrs]
      (into [:div.form-field-control] body)]
     [form-field-meta-list :error errors]
     [form-field-meta-list :warning warnings]]))

(def ^{:arglists '([attrs])} input
  (with-id
    (with-emit-on-change
      (with-trim-blur
        (with-disabled-compat
          (fn [attrs]
            [form-field
             attrs
             [comp/plain-input attrs]]))))))

(defn ^:private to-iso [date]
  #?(:cljs (letfn [(pad [num]
                     (str (when (< num 10) \0) num))]
             (str (.getFullYear date)
                  \- (pad (inc (.getMonth date)))
                  \- (pad (.getDate date))
                  \T (pad (.getHours date))
                  \: (pad (.getMinutes date))))))

(def ^{:arglists '([attrs])} datetime
  (with-id
    (with-emit-on-change
      (with-disabled-compat
        (fn [attrs]
          (let [attrs' (-> attrs
                           (assoc :type :datetime-local)
                           #?@(:cljs [(maps/update-when :value to-iso)
                                      (update :on-change comp #(some-> % not-empty js/Date.))]))]
            [form-field
             attrs
             [comp/plain-input attrs']]))))))

(def ^{:arglists '([attrs])} textarea
  (with-id
    (with-emit-on-change
      (with-trim-blur
        (with-disabled-compat
          (fn [{:keys [on-change value] :as attrs}]
            [form-field
             attrs
             [:textarea.textarea
              (-> {:value     value
                   :on-change (comp on-change dom/target-value)}
                  (merge (select-keys attrs #{:class :disabled :id :on-blur :ref})))]]))))))

(def ^{:arglists '([attrs options])} select
  (with-id
    (with-emit-on-change
      (with-disabled-compat
        (fn [{:keys [on-change value] :as attrs} options]
          (let [option-values (set (map first options))
                value (if (contains? option-values value)
                        value
                        ::empty)]
            [form-field
             attrs
             [:div.select
              [:select
               (-> {:value     (str value)
                    :on-change (comp on-change
                                     (into {} (map (juxt str identity) option-values))
                                     dom/target-value)}
                   (merge (select-keys attrs #{:class :disabled :id :on-blur :ref})))
               (for [[option label attrs] (cond->> options
                                            (= ::empty value)
                                            (cons [::empty "Choose..." {:disabled true}]))
                     :let [str-option (str option)]]
                 ^{:key str-option}
                 [:option
                  (assoc attrs :value str-option)
                  label])]]]))))))

(def ^{:arglists '([attrs])} tags-editor
  (with-id
    (with-emit-on-change
      (with-disabled-compat
        (fn [attrs]
          [form-field
           attrs
           [tags-editor/control attrs]])))))

(def ^{:arglists '([attrs])} type-ahead
  (with-id
    (with-emit-on-change
      (with-disabled-compat
        (fn [attrs]
          [form-field
           attrs
           [type-ahead/control attrs]])))))

(def ^{:arglists '([attrs])} multi-dropdown
  (with-id
    (with-emit-on-change
      (with-disabled-compat
        (fn [attrs]
          [form-field
           attrs
           [dd/control attrs]])))))

(def ^{:arglists '([attrs])} single-dropdown
  (with-id
    (with-emit-on-change
      (with-disabled-compat
        (fn [attrs]
          [form-field
           attrs
           [dd/control (dd/singleable attrs)]])))))

(defn ^:private form-button-row [{:keys [attempted? buttons disabled requesting?] :as attrs}]
  (cond-> [:div.button-row.layout--room-between
           [comp/plain-button
            {:class    ["is-primary" "submit" (when attempted? "disabled")]
             :type     :submit
             :disabled (disabled-compat disabled)}
            (:submit/body attrs "Submit")]]

    requesting?
    (conj [:div.space--bottom [comp/spinner]])

    buttons
    (into buttons)))

(defn plain-form [{:keys [disabled form+ horizontal?] :as attrs} & fields]
  (let [changed? (forms/changed? form+)
        errors (when (res/error? form+)
                 (res/payload form+))
        local-errors (or (::forms/errors errors) errors)
        form-errors (when (vector? local-errors)
                      local-errors)
        requesting? (res/requesting? form+)
        init? (res/init? form+)
        any-errors? (and errors (not init?))]
    [:form.form
     (-> (select-keys attrs #{:class :style :on-submit})
         (update :on-submit comp dom/prevent-default!)
         (cond->
           any-errors? (update :class conj "errors")
           changed? (update :class conj "is-changed")))
     (into [:div {:class [(if horizontal?
                            "layout--space-between"
                            "layout--stack-between")]}]
           fields)
     (when (and form-errors (not init?))
       [form-field-meta-list :error form-errors true])
     [form-button-row (assoc attrs
                             :attempted? (and (not init?) (not changed?) errors)
                             :disabled (or disabled requesting?)
                             :requesting? requesting?)]]))

(defn form [{:keys [*:store params resource-key] :as attrs} & fields]
  (into [plain-form (assoc attrs
                           :on-submit
                           (fn [_]
                             (store/dispatch! *:store [::forms+/submit! resource-key params])))]
        fields))

(def ^{:arglists '([attrs form+ path])} with-attrs
  "Prepares common form attributes used by controls in [[brainard.infra.views.controls.core]]. "
  shared/with-attrs)
