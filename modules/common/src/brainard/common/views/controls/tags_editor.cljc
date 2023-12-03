(ns brainard.common.views.controls.tags-editor
  "A tags editor reagent component."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.keywords :as kw]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.type-ahead :as type-ahead]
    [clojure.string :as string]))

(def ^:private ^:const tag-re #"([a-z][a-z0-9-\.]*/)?[a-z][a-z0-9-]*")

(defn ^:private ->add-tag [{:keys [*:store on-change value]} form-id form-data]
  (fn [e]
    (dom/prevent-default! e)
    (when-let [input-val (some-> (:value form-data) string/trim not-empty)]
      (if (re-matches tag-re input-val)
        (do (on-change (conj value (keyword input-val)))
            (store/dispatch! *:store [:forms/change form-id [:value] nil]))
        (store/dispatch! *:store [:forms/change form-id [:invalid?] true])))))

(defn ^:private ->update-form [*:store form-id]
  (fn [next-value]
    (let [next-value (cond-> next-value
                       (keyword? next-value) kw/str)]
      (store/dispatch! *:store [:forms/change form-id [:value] next-value])
      (store/dispatch! *:store [:forms/change form-id [:invalid?] false]))))

(defn control [{:keys [*:store] :as attrs}]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (store/dispatch! *:store [:forms/create $id])))
               sub:form (store/subscribe *:store [:forms/form form-id])
               on-change (->update-form *:store form-id)]
    (let [form @sub:form
          form-data (forms/data form)]
      [:div.tags-editor
       [:div.field.has-addons
        [type-ahead/control (-> attrs
                                (forms/with-attrs form
                                                  (:sub:items attrs)
                                                  [:value])
                                (assoc :placeholder "Add tag..."
                                       :on-change on-change))]
        [:button.button.is-link {:on-click (->add-tag attrs form-id form-data)}
         "+"]]
       (when (:invalid? form-data)
         [:span "invalid tag"])
       [comp/tag-list attrs]])
    (finally
      (store/dispatch! *:store [:forms/destroy form-id]))))
