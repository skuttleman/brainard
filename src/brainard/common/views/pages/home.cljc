(ns brainard.common.views.pages.home
  (:require
    [brainard.common.forms :as forms]
    [brainard.common.specs :as specs]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [clojure.pprint :as pp]))

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(def ^:private new-note-validator
  (specs/->validator specs/new-note))

(defn root [_]
  (r/with-let [form-id (doto (random-uuid)
                         (as-> $id (rf/dispatch [:forms/create $id new-note])))
               sub:form (rf/subscribe [:forms/form form-id])
               sub:contexts (rf/subscribe [:resources/resource :api.contexts/fetch])
               sub:tags (rf/subscribe [:resources/resource :api.tags/fetch])
               sub:res (rf/subscribe [:resources/resource [:api.notes/create! form-id]])]
    (let [form @sub:form
          data (forms/data form)
          errors (new-note-validator data)]
      [ctrls/form {:sub:res   sub:res
                   :errors    errors
                   :on-submit [:api.notes/create! form-id {:data     data
                                                           :reset-to new-note}]}
       [:strong "Create a note"]
       [ctrls/type-ahead (-> {:label     "Context"
                              :sub:items sub:contexts}
                             (forms/with-attrs form
                                               sub:res
                                               [:notes/context]
                                               new-note-validator))]
       [ctrls/textarea (-> {:label "Body"}
                           (forms/with-attrs form
                                             sub:res
                                             [:notes/body]
                                             new-note-validator))]
       [ctrls/tags-editor (-> {:label     "Tags"
                               :sub:items sub:tags}
                              (forms/with-attrs form
                                                sub:res
                                                [:notes/tags]
                                                new-note-validator))]])
    (finally
      (rf/dispatch [:resources/destroy [:api.notes/create! form-id]])
      (rf/dispatch [:forms/destroy form-id]))))
