(ns brainard.common.views.controls.tags-editor
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.keywords :as kw]
    [brainard.common.views.controls.type-ahead :as type-ahead]
    [clojure.string :as string]))

(def ^:private ^:const tag-re #"[a-z]([a-z0-9-]\.?)*(/[a-z][a-z0-9-]*)?")

(defn ^:private ->add-tag [{:keys [on-change value] :as attrs} form-id form-data]
  (fn [e]
    (dom/prevent-default! e)
    (when-let [input-val (some-> (:value form-data) string/trim not-empty)]
      (when (re-matches tag-re input-val)
        (on-change (conj value (keyword input-val)))
        (rf/dispatch-sync [:forms/change form-id [:value] nil])))))

(defn ^:private ->update-form [form-id]
  (fn [next-value]
    (let [next-value (cond-> next-value
                       (keyword? next-value) kw/str)]
      (rf/dispatch-sync [:forms/change form-id [:value] next-value]))))

(defn ^:private tag-list [{:keys [on-change value]}]
  [:div.field.is-grouped.is-grouped-multiline.layout--space-between
   (for [tag value]
     ^{:key tag}
     [:div.tags.has-addons
      [:span.tag.is-info.is-light (str tag)]
      [:a.tag.is-delete.link {:href     "#"
                              :on-click (fn [e]
                                          (dom/prevent-default! e)
                                          (on-change (disj value tag)))}]])])

(defn control [attrs]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id])))
               sub:form (rf/subscribe [:forms/form form-id])
               on-change (->update-form form-id)]
    (let [form @sub:form]
      (letfn []
        [:div.tags-editor
         [:div.field.has-addons
          [type-ahead/control (-> attrs
                                  (forms/with-attrs form
                                                    (:sub:items attrs)
                                                    [:value])
                                  (assoc :placeholder "Add tag..."
                                         :on-change on-change))]
          [:button.button.is-link {:on-click (->add-tag attrs form-id (forms/data form))}
           "+"]]
         [tag-list attrs]]))
    (finally
      (rf/dispatch [:forms/destroy form-id]))))
