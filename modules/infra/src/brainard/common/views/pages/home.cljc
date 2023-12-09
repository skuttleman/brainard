(ns brainard.common.views.pages.home
  "The home page with note creation form."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.validations.core :as valid]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [brainard.common.views.pages.shared :as spages]))

(def ^:private ^:const form-id
  ::forms/new-note)

(def ^:private ^:const note-res
  ::notes)

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(def ^:private new-note-validator
  (valid/->validator valid/new-note))

(defn ^:private root* [{:keys [*:store sub:contexts sub:form sub:res sub:tags]}]
  (let [form @sub:form
        data (forms/data form)
        errors (new-note-validator data)]
    [ctrls/form {:*:store      *:store
                 :form         form
                 :errors       errors
                 :params       {:data     data
                                :reset-to new-note}
                 :resource-key [::rspecs/notes#create form-id]
                 :sub:res      sub:res}
     [:strong "Create a note"]
     [ctrls/type-ahead (-> {:*:store   *:store
                            :label     "Context"
                            :sub:items sub:contexts
                            :on-blur   (fn [e]
                                         (store/dispatch! *:store
                                                          [:resources/sync!
                                                           [::rspecs/notes#select note-res]
                                                           {:notes/context (dom/target-value e)}]))}
                           (ctrls/with-attrs form sub:res [:notes/context] errors))]
     [ctrls/textarea (-> {:label   "Body"
                          :*:store *:store}
                         (ctrls/with-attrs form sub:res [:notes/body] errors))]
     [ctrls/tags-editor (-> {:*:store   *:store
                             :label     "Tags"
                             :sub:items sub:tags}
                            (ctrls/with-attrs form sub:res [:notes/tags] errors))]]))

(defn ^:private search-results [opts [notes]]
  (when (seq notes)
    [:div
     [:h3.subtitle [:em "Some related notes..."]]
     [spages/search-results opts [notes]]]))

(defmethod ipages/page :routes.ui/home
  [{:keys [*:store]}]
  (r/with-let [_ (store/dispatch! *:store [:forms/ensure! form-id new-note])
               sub:form (store/subscribe *:store [:forms/?:form form-id])
               sub:contexts (store/subscribe *:store [:resources/?:resource ::rspecs/contexts#select])
               sub:tags (store/subscribe *:store [:resources/?:resource ::rspecs/tags#select])
               sub:res (store/subscribe *:store [:resources/?:resource [::rspecs/notes#create form-id]])
               sub:notes (store/subscribe *:store [:resources/?:resource [::rspecs/notes#select note-res]])]
    [:div
     [root* {:*:store      *:store
             :sub:contexts sub:contexts
             :sub:form     sub:form
             :sub:res      sub:res
             :sub:tags     sub:tags}]
     [comp/with-resources [sub:notes] [search-results {:hide-init? true}]]]
    (finally
      (store/emit! *:store [:resources/destroyed [::rspecs/notes#select note-res]])
      (store/emit! *:store [:resources/destroyed [::rspecs/notes#create form-id]])
      (store/emit! *:store [:forms/destroyed form-id]))))
