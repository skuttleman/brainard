(ns brainard.common.views.pages.home
  "The home page with note creation form."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.services.store.core :as store]
    [brainard.common.services.validations.core :as valid]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]))

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(def ^:private new-note-validator
  (valid/->validator valid/new-note))

(defn ^:private root* [{:keys [form-id sub:contexts sub:form sub:res sub:tags]}]
  (let [form @sub:form
        data (forms/data form)
        errors (new-note-validator data)]
    [ctrls/form {:form         form
                 :errors       errors
                 :params       {:data     data
                                :reset-to new-note}
                 :resource-key [:api.notes/create! form-id]
                 :sub:res      sub:res}
     [:strong "Create a note"]
     [ctrls/type-ahead (-> {:label     "Context"
                            :sub:items sub:contexts}
                           (forms/with-attrs form
                                             sub:res
                                             [:notes/context]
                                             errors))]
     [ctrls/textarea (-> {:label "Body"}
                         (forms/with-attrs form
                                           sub:res
                                           [:notes/body]
                                           errors))]
     [ctrls/tags-editor (-> {:label     "Tags"
                             :sub:items sub:tags}
                            (forms/with-attrs form
                                              sub:res
                                              [:notes/tags]
                                              errors))]]))

(defmethod ipages/page :routes.ui/home
  [_]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (store/dispatch [:forms/create $id new-note])))
               sub:form (store/subscribe [:forms/form form-id])
               sub:contexts (store/subscribe [:resources/resource :api.contexts/select])
               sub:tags (store/subscribe [:resources/resource :api.tags/select])
               sub:res (store/subscribe [:resources/resource [:api.notes/create! form-id]])]
    [root* {:form-id      form-id
            :sub:contexts sub:contexts
            :sub:form     sub:form
            :sub:res      sub:res
            :sub:tags     sub:tags}]
    (finally
      (store/dispatch [:resources/destroy [:api.notes/create! form-id]])
      (store/dispatch [:forms/destroy form-id]))))
