(ns brainard.common.views.pages.home
  (:require
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

(defn ^:private with-attrs [attrs form-id path data warnings errors]
  (assoc attrs
         :value (get-in data path)
         :warnings (get-in warnings path)
         :errors (get-in errors path)
         :on-change [:forms/change form-id path]))

(defmethod pages.common/page :routes.ui/home
  [_]
  (let [form-id (random-uuid)
        sub:data (rf/subscribe [:forms/value form-id])
        sub:status (rf/subscribe [:forms/status form-id])
        sub:warnings (rf/subscribe [:forms/warnings form-id])
        sub:errors (rf/subscribe [:forms/errors form-id])]
    (rf/dispatch [:forms/create form-id new-note])
    (r/create-class
      {:component-will-unmount
       (fn [_]
         (rf/dispatch [:forms/destroy form-id]))
       :reagent-render
       (fn [_]
         (let [data @sub:data
               status @sub:status
               warnings @sub:warnings
               errors @sub:errors]
           [ctrl/form {:ready?    (#{:warning :modified :init :error} status)
                       :valid?    (#{:warning :waiting :modified} status)
                       :disabled  (#{:waiting} status)
                       :on-submit [:api.notes/create! {:form-id  form-id
                                                       :data     data
                                                       :reset-to new-note}]}
            [:pre (with-out-str (pp/pprint [status data errors]))]
            [:strong "Create a note"]
            [ctrl/input (with-attrs {:label "Context"}
                                    form-id
                                    [:notes/context]
                                    data
                                    warnings
                                    errors)]
            [ctrl/textarea (with-attrs {:label "Body"}
                                       form-id
                                       [:notes/body]
                                       data
                                       warnings
                                       errors)]]))})))
