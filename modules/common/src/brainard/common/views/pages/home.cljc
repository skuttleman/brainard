(ns brainard.common.views.pages.home
  "The home page with note creation form."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.validations.core :as valid]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]))

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(def ^:private new-note-validator
  (valid/->validator valid/new-note))

(defn ^:private root* [{:keys [*:store form-id sub:contexts sub:form sub:res sub:tags]}]
  (let [form @sub:form
        data (forms/data form)
        errors (new-note-validator data)]
    [ctrls/form {:*:store      *:store
                 :form         form
                 :errors       errors
                 :params       {:data     data
                                :reset-to new-note}
                 :resource-key [:api.notes/create! form-id]
                 :sub:res      sub:res}
     [:strong "Create a note"]
     [ctrls/type-ahead (-> {:*:store   *:store
                            :label     "Context"
                            :sub:items sub:contexts}
                           (forms/with-attrs form
                                             sub:res
                                             [:notes/context]
                                             errors))]
     [ctrls/textarea (-> {:label   "Body"
                          :*:store *:store}
                         (forms/with-attrs form
                                           sub:res
                                           [:notes/body]
                                           errors))]
     [ctrls/tags-editor (-> {:*:store   *:store
                             :label     "Tags"
                             :sub:items sub:tags}
                            (forms/with-attrs form
                                              sub:res
                                              [:notes/tags]
                                              errors))]]))

(defmethod ipages/page :routes.ui/home
  [{:keys [*:store]}]
  (r/with-let [form-id (doto ::forms/new-note
                         (as-> $id (store/dispatch! *:store [:forms/ensure! $id new-note])))
               sub:form (store/subscribe *:store [:forms/?form form-id])
               sub:contexts (store/subscribe *:store [:resources/?resource :api.contexts/select!])
               sub:tags (store/subscribe *:store [:resources/?resource :api.tags/select!])
               sub:res (store/subscribe *:store [:resources/?resource [:api.notes/create! form-id]])]
    [root* {:*:store      *:store
            :form-id      form-id
            :sub:contexts sub:contexts
            :sub:form     sub:form
            :sub:res      sub:res
            :sub:tags     sub:tags}]
    (finally
      (store/dispatch! *:store [:resources/destroyed [:api.notes/create! form-id]])
      (store/dispatch! *:store [:forms/destroyed form-id]))))
