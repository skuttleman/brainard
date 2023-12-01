(ns brainard.common.views.pages.notes
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.services.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [clojure.set :as set]))

(defn ^:private diff-tags [old new]
  (let [removals (set/difference old new)]
    {:notes/tags#removed removals
     :notes/tags         new}))

(defn ^:private tag-editor [{:keys [form-id form sub:res sub:tags]} note]
  (let [data (forms/data form)
        cancel-event [:forms/create form-id {:notes/tags (:notes/tags note)
                                             ::editing?  false}]]
    [ctrls/form {:params       {:note-id  (:notes/id note)
                                :old      note
                                :data     (diff-tags (:notes/tags note) (:notes/tags data))
                                :fetch?   true
                                :reset-to (assoc data ::editing? false)}
                 :resource-key [:api.notes/update! form-id]
                 :sub:res      sub:res
                 :submit/body  "Save"
                 :buttons      [[:button.button.is-cancel
                                 {:on-click (fn [e]
                                              (dom/prevent-default! e)
                                              (store/dispatch cancel-event))}
                                 "Cancel"]]}
     [ctrls/tags-editor (-> {:label     "Tags"
                             :sub:items sub:tags}
                            (forms/with-attrs form
                                              sub:res
                                              [:notes/tags]))]]))

(defn ^:private tag-list [{:keys [form-id]} note]
  [:div.layout--space-between
   (if-let [tags (not-empty (:notes/tags note))]
     [comp/tag-list {:value tags}]
     [:em "no tags"])
   [:button.button {:on-click (fn [_]
                                (store/dispatch [:forms/change form-id [::editing?] true]))}
    "edit tags"]])

(defn ^:private root* [note]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (store/dispatch [:forms/create $id {:notes/tags (:notes/tags note)
                                                                    ::editing?     false}])))
               sub:form (store/subscribe [:forms/form form-id])
               sub:res (store/subscribe [:resources/resource [:api.notes/update! form-id]])
               sub:tags (store/subscribe [:resources/resource :api.tags/select])]
    (let [form @sub:form
          attrs {:form-id  form-id
                 :form     form
                 :sub:res  sub:res
                 :sub:tags sub:tags}]
      [:div.layout--stack-between
       [:h1 [:strong (:notes/context note)]]
       [:p (:notes/body note)]
       (if (::editing? (forms/data form))
         [tag-editor attrs note]
         [tag-list attrs note])])
    (finally
      (store/dispatch [:forms/destroy form-id]))))

(defmethod ipages/page :routes.ui/note
  [{:keys [route-params]}]
  (r/with-let [sub:note (do (store/dispatch [:resources/submit! [:api.notes/find (:notes/id route-params)]])
                            (store/subscribe [:resources/resource [:api.notes/find (:notes/id route-params)]]))]
    [comp/with-resource sub:note [root*]]
    (finally
      (store/dispatch [:resources/destroy [:api.notes/find (:notes/id route-params)]]))))
