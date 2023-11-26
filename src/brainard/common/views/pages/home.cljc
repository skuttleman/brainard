(ns brainard.common.views.pages.home
  (:require
    [brainard.common.views.controls :as ctrl]
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

(defn root [_]
  (r/with-let [form-id (random-uuid)
               sub:data (rf/subscribe [:forms/value form-id])
               sub:status (rf/subscribe [:forms/status form-id])
               sub:warnings (rf/subscribe [:forms/warnings form-id])
               sub:errors (rf/subscribe [:forms/errors form-id])
               sub:contexts (rf/subscribe [:core/contexts])
               _ (rf/dispatch [:forms/create form-id new-note])]
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
       [:strong "Create a note"]
       [ctrl/type-ahead (with-attrs {:label     "Context"
                                     :sub:items sub:contexts}
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
                                  errors)]])
    (finally
      (rf/dispatch [:forms/destroy form-id]))))
