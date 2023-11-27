(ns brainard.common.views.controls.tags-editor
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.controls.type-ahead :as type-ahead]
    [clojure.string :as string]))

(defn ^:private kw-str [kw]
  (if-let [[ns name] (when (keyword? kw)
                       [(namespace kw) (name kw)])]
    (cond->> (str name) ns (str ns "/"))
    (str kw)))

(def ^:private kw-re #"[a-z]([a-z0-9-]\.?)*(/[a-z][a-z0-9-]*)?")

(defn control [{:keys [on-change value] :as attrs}]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id])))
               sub:form (rf/subscribe [:forms/form form-id])]
    (let [form @sub:form
          form-data (forms/model form)]
      (letfn [(add-tag [e]
                (dom/prevent-default! e)
                (when-let [input-val (some-> (:value form-data) string/trim not-empty)]
                  (when (re-matches kw-re input-val)
                    (on-change (conj value (keyword input-val)))
                    (rf/dispatch-sync [:forms/change form-id [:value] nil]))))
              (update-form [value]
                (rf/dispatch-sync [:forms/change form-id [:value] (kw-str value)]))]
        [:div.tags-editor
         [:div.field.has-addons
          [type-ahead/control (assoc attrs
                                     :placeholder "Add tag..."
                                     :value (:value form-data)
                                     :on-change update-form)]
          [:button.button.is-link {:on-click add-tag}
           "+"]]
         [:div.field.is-grouped.is-grouped-multiline.layout--space-between
          (for [tag value]
            ^{:key tag}
            [:div.tags.has-addons
             [:span.tag.is-info.is-light (str tag)]
             [:a.tag.is-delete.link {:href     "#"
                                     :on-click (fn [e]
                                                 (dom/prevent-default! e)
                                                 (on-change (disj value tag)))}]])]]))))
