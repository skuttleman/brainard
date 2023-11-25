(ns brainard.common.views.pages.home
  (:require
    [brainard.common.specs :as specs]
    [brainard.common.views.controls :as ctrl]
    [brainard.common.views.pages.common :as pages.common]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]
    [clojure.pprint :as pp]))

(def ^:private
  new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(def ^:private new-note-validator
  (specs/->validator specs/new-note))

(defmethod pages.common/page :routes.ui/home
  [_]
  (let [form-id (random-uuid)
        sub:data (rf/subscribe [:forms/value form-id])]
    (rf/dispatch [:forms/create form-id new-note])
    (r/create-class
      {:component-will-unmount
       (fn [_]
         (rf/dispatch [:forms/destroy form-id]))
       :reagent-render
       (fn [_]
         (let [data @sub:data
               errors (new-note-validator data)]
           [ctrl/form {:ready?    true
                       :valid?    true
                       :disabled  false
                       :on-submit [:api.notes/create! {:data    data
                                                       :form-id form-id
                                                       :reset   new-note}]}
            [:pre (with-out-str (pp/pprint [data errors]))]
            [:strong "Create a note"]
            [ctrl/input {:label     "Context"
                         :value     (:notes/context data)
                         :on-change [:forms/change form-id [:notes/context]]}]
            [ctrl/textarea {:label     "Body"
                            :value     (:notes/body data)
                            :on-change [:forms/change form-id [:notes/body]]}]]))})))
