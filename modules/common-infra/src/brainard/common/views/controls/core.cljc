(ns brainard.common.views.controls.core
  "All controls in this namespace take a store event vector as on-change. The changed value
   will be [[conj]]'d onto the event. The following will call (dispatch [:my-event target-value])

   [input {:on-change [:my-event]}]"
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.views.controls.shared :as shared]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.fns :as fns]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.dropdown :as dd]
    [brainard.common.views.controls.tags-editor :as tags-editor]
    [brainard.common.views.controls.type-ahead :as type-ahead]
    [clojure.string :as string]))

(defn ^:private disabled-compat [disabled]
  #?(:clj true :default disabled))

(defn ^:private dispatch-on-change [on-change *:store]
  (fn [value]
    (when on-change
      (store/dispatch! *:store (conj on-change value)))))

(defn ^:private with-dispatch-on-change [component]
  (fn [{:keys [*:store] :as attrs} & args]
    (into [component (update attrs :on-change dispatch-on-change *:store)] args)))

(defn ^:private with-id [component]
  (fn [attrs & args]
    (r/with-let [id (gensym "form-field")]
      (into [component (assoc attrs :id id)] args))))

(defn ^:private with-trim-blur [component]
  (fn [attrs & args]
    (-> attrs
        (update :on-blur fns/apply-all!
                (fn [_]
                  (when-let [on-change (:on-change attrs)]
                    (on-change (some-> attrs :value string/trim not-empty)))))
        (->> (conj [component]))
        (into args))))

(defn ^:private form-field-label [{:keys [id label label-small?]}]
  (when label
    [:label.label
     (cond-> {:html-for id}
       label-small? (assoc :style {:font-weight :normal
                                   :font-size   "0.8em"}))
     label]))

(defn ^:private form-field-meta-list [type items]
  (when (seq items)
    [:ul {:class [(str (name type) "-list")]}
     (for [item items]
       ^{:key item}
       [:li {:class [(name type)]}
        item])]))

(defn ^:private form-field [{:keys [errors form-field-class warnings] :as attrs} & body]
  (let [errors (seq (remove nil? errors))]
    [:div.form-field
     {:class (into [(when errors "errors") (when warnings "warnings")] form-field-class)}
     [:<>
      [form-field-label attrs]
      (into [:div.form-field-control] body)]
     [form-field-meta-list :error errors]
     [form-field-meta-list :warning warnings]]))

(def ^{:arglists '([attrs])} textarea
  (with-id
    (with-dispatch-on-change
      (with-trim-blur
        (fn [{:keys [disabled on-change value] :as attrs}]
          [form-field
           attrs
           [:textarea.textarea
            (-> {:value     value
                 :disabled  (disabled-compat disabled)
                 :on-change (comp on-change dom/target-value)}
                (merge (select-keys attrs #{:class :id :on-blur :ref})))]])))))

(def ^{:arglists '([attrs])} tags-editor
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         (update attrs :disabled disabled-compat)
         [tags-editor/control attrs]]))))

(def ^{:arglists '([attrs])} type-ahead
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         (update attrs :disabled disabled-compat)
         [type-ahead/control attrs]]))))

(def ^{:arglists '([attrs])} multi-dropdown
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         (update attrs :disabled disabled-compat)
         [dd/control attrs]]))))

(def ^{:arglists '([attrs])} single-dropdown
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         (update attrs :disabled disabled-compat)
         [dd/control (dd/singleable attrs)]]))))

(defn ^:private form-button-row [{:keys [buttons disabled requesting?] :as attrs}]
  (cond-> [:div.button-row
           [comp/plain-button
            {:class    ["is-primary" "submit"]
             :type     :submit
             :disabled (disabled-compat disabled)}
            (:submit/body attrs "Submit")]]

    requesting?
    (conj [:div {:style {:margin-bottom "8px"}} [comp/spinner]])

    buttons
    (into buttons)))

(defn form [{:keys [*:store errors form params resource-key sub:res] :as attrs} & fields]
  (let [form-errors (when (vector? errors) errors)
        [status] @sub:res
        requesting? (= :requesting status)
        init? (= :init status)
        any-errors? (and errors (not init?))]
    (-> [:form.form.layout--stack-between
         (-> {:on-submit (fn [e]
                           (dom/prevent-default! e)
                           (when (or init? (forms/changed? form))
                             (if errors
                               (store/dispatch! *:store [::store/emit! [:resources/failed resource-key :local errors]])
                               (store/dispatch! *:store [:resources/submit! resource-key params]))))}
             (merge (select-keys attrs #{:class :style}))
             (cond-> any-errors? (update :class conj "errors")))]
        (into fields)
        (cond->
          (and form-errors (not init?))
          (conj [form-field-meta-list :error errors]))
        (conj [form-button-row (-> attrs
                                   (update :disabled (comp disabled-compat fns/or) requesting? any-errors?)
                                   (assoc :requesting? requesting?))]))))

(def ^{:arglists '([attrs form sub:res path] [attrs form sub:res path errors])} with-attrs
  "Prepares common form attributes used by controls in [[brainard.common.views.controls.core]]. "
  shared/with-attrs)
