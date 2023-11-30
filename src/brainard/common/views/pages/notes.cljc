(ns brainard.common.views.pages.notes
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.main :as views.main]
    [clojure.set :as set]))

(defn ^:private diff-tags [old new]
  (let [removals (set/difference old new)]
    {:notes.retract/tags removals
     :notes/tags         new}))

(defn ^:private tag-editor [{:keys [form-id form sub:res sub:tags]} note]
  (let [data (forms/data form)]
    [ctrls/form {:params       {:note-id  (:notes/id note)
                                :old      note
                                :data     (diff-tags (:notes/tags note) (:notes/tags data))
                                :fetch?   true
                                :reset-to (assoc data ::editing? false)}
                 :resource-key [:api.notes/update! form-id]
                 :sub:res      sub:res}
     [ctrls/tags-editor (-> {:label     "Tags"
                             :sub:items sub:tags}
                            (forms/with-attrs form
                                              sub:res
                                              [:notes/tags]))]]))

(defn ^:private tag-list [{:keys [form-id]} note]
  [:div.layout--space-between
   [ctrls/tag-list {:value (:notes/tags note)}]
   [:button.button {:on-click (fn [_]
                                (rf/dispatch [:forms/change form-id [::editing?] true]))}
    "edit tags"]])

(defn ^:private root* [note]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id {:notes/tags (:notes/tags note)
                                                                    ::editing?  false}])))
               sub:form (rf/subscribe [:forms/form form-id])
               sub:res (rf/subscribe [:resources/resource [:api.notes/update! form-id]])
               sub:tags (rf/subscribe [:resources/resource :api.tags/select])]
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
      (rf/dispatch [:forms/destroy form-id]))))

(defn root [{:keys [route-params]}]
  (r/with-let [sub:note (do (rf/dispatch [:resources/submit! [:api.notes/find (:notes/id route-params)]])
                            (rf/subscribe [:resources/resource [:api.notes/find (:notes/id route-params)]]))]
    [views.main/with-resource sub:note [root*]]
    (finally
      (rf/dispatch [:resources/destroy [:api.notes/find (:notes/id route-params)]]))))
