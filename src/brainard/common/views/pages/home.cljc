(ns brainard.common.views.pages.home
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.specs :as specs]
    [brainard.common.validations :as valid]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]))

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(def ^:private new-note-validator
  (valid/->validator specs/new-note))

(defn ^:private root* [{:keys [form-id sub:contexts sub:form sub:res sub:tags]}]
  (let [form @sub:form
        data (forms/data form)
        errors (new-note-validator data)]
    [ctrls/form {:errors       errors
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

(defn root [_]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id new-note])))
               sub:form (rf/subscribe [:forms/form form-id])
               sub:contexts (rf/subscribe [:resources/resource :api.contexts/select])
               sub:tags (rf/subscribe [:resources/resource :api.tags/select])
               sub:res (rf/subscribe [:resources/resource [:api.notes/create! form-id]])]
    [root* {:form-id      form-id
            :sub:contexts sub:contexts
            :sub:form     sub:form
            :sub:res      sub:res
            :sub:tags     sub:tags}]
    (finally
      (rf/dispatch [:resources/destroy [:api.notes/create! form-id]])
      (rf/dispatch [:forms/destroy form-id]))))
