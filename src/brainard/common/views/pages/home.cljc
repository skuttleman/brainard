(ns brainard.common.views.pages.home
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]))

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(defn ^:private with-attrs [attrs form-id path data warnings errors]
  (assoc attrs
         :value (get-in data path)
         :warnings (get-in warnings path)
         :errors (get-in errors path)
         :on-change [:forms/change form-id path]))

(defn root [_]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id new-note])))
               sub:form (rf/subscribe [:forms/form form-id])
               sub:contexts (rf/subscribe [:core/contexts])
               sub:tags (rf/subscribe [:core/tags])]
    (let [form @sub:form
          [data status warnings errors] (map #(% form) [forms/model
                                                        forms/status
                                                        forms/warnings
                                                        forms/errors])
          [tags-status tag-options] @sub:tags]
      [ctrls/form {:ready?    (#{:warning :modified :init :error} status)
                   :valid?    (#{:warning :waiting :modified} status)
                   :disabled  (#{:waiting} status)
                   :on-submit [:api.notes/create! {:form-id  form-id
                                                   :data     data
                                                   :reset-to new-note}]}
       [:strong "Create a note"]
       [ctrls/type-ahead (with-attrs {:label     "Context"
                                      :sub:items sub:contexts}
                                     form-id
                                     [:notes/context]
                                     data
                                     warnings
                                     errors)]
       [ctrls/textarea (with-attrs {:label "Body"}
                                   form-id
                                   [:notes/body]
                                   data
                                   warnings
                                   errors)]
       [ctrls/tags-editor (with-attrs {:label "Tags"
                                       :tags  (when (= :success tags-status)
                                                tag-options)}
                                      form-id
                                      [:notes/tags]
                                      data
                                      warnings
                                      errors)]])
    (finally
      (rf/dispatch [:forms/destroy form-id]))))
