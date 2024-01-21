(ns brainard.infra.views.controls.tags-editor
  "A tags-editor reagent component."
  (:require
    [brainard.api.utils.keywords :as kw]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.shared :as shared]
    [brainard.infra.views.controls.type-ahead :as type-ahead]
    [clojure.string :as string]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [whet.utils.reagent :as r]))

(def ^:private ^:const tag-re #"([a-z][a-z0-9-\.]*/)?[a-z][a-z0-9-]*")

(defn ^:private ->add-tag [{:keys [*:store on-change value]} form-id form-data]
  (fn [e]
    (dom/prevent-default! e)
    (when-let [input-val (some-> (:value form-data) string/trim not-empty)]
      (if (re-matches tag-re input-val)
        (do (on-change (conj value (keyword input-val)))
            (store/emit! *:store [::forms/changed form-id [:value] nil]))
        (store/emit! *:store [::forms/changed form-id [:invalid?] true])))))

(defn ^:private ->update-form [*:store form-id]
  (fn [next-value]
    (let [next-value (cond-> next-value
                       (keyword? next-value) kw/str)]
      (doto *:store
        (store/emit! [::forms/changed form-id [:value] next-value])
        (store/emit! [::forms/changed form-id [:invalid?] false])))))

(defn control [{:keys [*:store form-id] :as attrs}]
  (r/with-let [sub:form (do (store/emit! *:store [::forms/created form-id])
                            (store/subscribe *:store [::forms/?:form form-id]))
               on-change (->update-form *:store form-id)]
    (let [form+ (forms+/->form+ @sub:form @(:sub:items attrs))
          form-data (forms/data form+)]
      [:div.tags-editor
       [:div.field.has-addons
        [type-ahead/control (-> attrs
                                (shared/with-attrs form+ [:value])
                                (assoc :placeholder "Add tag..."
                                       :on-change on-change))]
        [:button.button.is-link {:tab-index -1
                                 :on-click  (->add-tag attrs form-id form-data)
                                 :disabled  #?(:clj true :default false)}
         "+"]]
       (when (:invalid? form-data)
         [:span.form-field.errors [:span.error-list [:span.error "invalid tag"]]])
       [comp/tag-list attrs]])
    (finally
      (store/emit! *:store [::forms/destroyed form-id]))))
