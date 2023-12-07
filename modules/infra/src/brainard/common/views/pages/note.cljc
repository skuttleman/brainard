(ns brainard.common.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.store.specs :as rspecs]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [clojure.set :as set]))

(def ^:private ^:const form-id
  ::forms/edit-note)

(defn ^:private diff-tags [old new]
  (let [removals (set/difference old new)]
    {:notes/tags!remove removals
     :notes/tags        new}))

(defn ^:private tag-editor [{:keys [*:store form sub:res sub:tags]} note]
  (let [data (forms/data form)
        cancel-event [:forms/created form-id {:notes/tags (:notes/tags note)
                                              ::editing?  false}]]
    [ctrls/form {:*:store      *:store
                 :form         form
                 :params       {:note-id  (:notes/id note)
                                :old      note
                                :data     (diff-tags (:notes/tags note) (:notes/tags data))
                                :fetch?   true
                                :reset-to (assoc data ::editing? false)}
                 :resource-key [::rspecs/notes#update form-id]
                 :sub:res sub:res
                 :submit/body "Save"
                 :buttons [[:button.button.is-cancel
                            {:on-click (fn [e]
                                         (dom/prevent-default! e)
                                         (store/emit! *:store cancel-event))}
                            "Cancel"]]}
     [ctrls/tags-editor (-> {:*:store   *:store
                             :label     "Tags"
                             :sub:items sub:tags}
                            (ctrls/with-attrs form
                                              sub:res
                                              [:notes/tags]))]]))

(defn ^:private tag-list [{:keys [*:store]} note]
  [:div.layout--space-between
   (if-let [tags (not-empty (:notes/tags note))]
     [comp/tag-list {:value tags}]
     [:em "no tags"])
   [:button.button {:disabled #?(:clj true :default false)
                    :on-click (fn [_]
                                (store/emit! *:store [:forms/changed form-id [::editing?] true]))}
    "edit tags"]])

(defn ^:private root* [*:store note]
  (r/with-let [_ (store/dispatch! *:store
                                  [:forms/ensure! form-id {:notes/tags (:notes/tags note)
                                                           ::editing?  false}])
               sub:form (store/subscribe *:store [:forms/form form-id])
               sub:res (store/subscribe *:store [:resources/?:resource [::rspecs/notes#update form-id]])
               sub:tags (store/subscribe *:store [:resources/?:resource ::rspecs/tags#select])]
    (let [form @sub:form
          attrs {:*:store  *:store
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
      (store/emit! *:store [:forms/destroyed form-id]))))

(defmethod ipages/page :routes.ui/note
  [{:keys [route-params *:store]}]
  (r/with-let [sub:note (do (store/dispatch! *:store [:resources/ensure! [::rspecs/notes#find (:notes/id route-params)]])
                            (store/subscribe *:store [:resources/?:resource [::rspecs/notes#find (:notes/id route-params)]]))]
    [comp/with-resource sub:note [root* *:store]]
    (finally
      (store/emit! *:store [:resources/destroyed [::rspecs/notes#find (:notes/id route-params)]]))))