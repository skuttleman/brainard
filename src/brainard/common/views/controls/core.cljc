(ns brainard.common.views.controls.core
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.utils.fns :as fns]
    [brainard.common.views.controls.dropdown :as dd]
    [brainard.common.views.controls.tags-editor :as tags-editor]
    [brainard.common.views.controls.type-ahead :as type-ahead]
    [brainard.common.views.main :as views.main]
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
       [:li {:class [(name type)]}
        {:key item}
        item])]))

(defn ^:private form-field [{:keys [errors form-field-class warnings] :as attrs} & body]
  (let [errors (seq (remove nil? errors))]
    [:div.form-field
     {:class (into [(cond errors "errors" warnings "warnings")] form-field-class)}
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
                 :disabled  disabled
                 :on-change (comp on-change dom/target-value)}
                (merge (select-keys attrs #{:class :id :on-blur :ref})))]])))))

(def ^{:arglists '([attrs])} input
  (with-id
    (with-dispatch-on-change
      (with-trim-blur
        (fn [attrs]
          [form-field
           attrs
           [views.main/plain-input attrs]])))))

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

(def ^{:arglists '([attrs])} multi-dropdown
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         attrs
         [dd/control attrs]]))))

(def ^{:arglists '([attrs])} single-dropdown
  (with-id
    (with-dispatch-on-change
      (fn [attrs]
        [form-field
         attrs
         [dd/control (dd/singleable attrs)]]))))

(defn ^:private form-button-row [{:keys [buttons disabled requesting?] :as attrs}]
  (cond-> [:div.button-row
                         [views.main/plain-button
                          {:class    ["is-primary" "submit"]
                           :type     :submit
                           :disabled disabled}
                          (:submit/text attrs "Submit")]]

                  requesting?
                  (conj [:div {:style {:margin-bottom "8px"}} [views.main/spinner]])

                  buttons
                  (into buttons)))

(defn form [{:keys [errors on-submit sub:res] :as attrs} & fields]
  (let [[status] @sub:res
        requesting? (= :requesting status)
        init? (= :init status)]
    (-> [:form.form.layout--stack-between
         (merge {:on-submit (fn [e]
                              (dom/prevent-default! e)
                              (rf/dispatch on-submit))}
                (select-keys attrs #{:class :style}))]
        (into fields)
        (conj [form-button-row (-> attrs
                                   (update :disabled #(or %
                                                          requesting?
                                                          (and errors (not init?))))
                                   (assoc :requesting? requesting?))]))))
