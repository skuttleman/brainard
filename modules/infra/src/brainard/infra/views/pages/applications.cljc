(ns brainard.infra.views.pages.applications
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as-alias forms+]
    [whet.utils.reagent :as r]))

(def ^:private ^:const new-app-form-key
  [::forms+/valid [::specs/app#new]])

(defn ^:private app-form [{:keys [*:store form+] :as attrs}]
  [ctrls/form attrs
   [:fieldset.fieldset
    [:legend "Company"]
    [ctrls/input (-> {:label       "Name"
                      :auto-focus? true
                      :*:store     *:store}
                     (ctrls/with-attrs form+ [:applications/company :companies/name]))]
    [ctrls/input (-> {:label   "Website"
                      :*:store *:store}
                     (ctrls/with-attrs form+ [:applications/company :companies/website]))]
    [ctrls/input (-> {:label   "Location"
                      :*:store *:store}
                     (ctrls/with-attrs form+ [:applications/company :companies/location]))]]
   [ctrls/input (-> {:label   "Role / Title"
                     :*:store *:store}
                    (ctrls/with-attrs form+ [:applications/job-title]))]
   [ctrls/textarea (-> {:label   "Misc"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:applications/details]))]])

(defmethod icomp/modal-header ::modal
  [_ _]
  [:div "Create new application"])

(defmethod icomp/modal-body ::modal
  [*:store {modal-id :modals/id :modals/keys [close!]}]
  (r/with-let [sub:form+ (-> *:store
                             (store/emit! [::forms/created
                                           new-app-form-key
                                           {:applications/company {::present? true}}
                                           {:remove-nil? true}])
                             (store/subscribe [::forms+/?:form+ new-app-form-key]))]
    [:div {:style {:min-width "50vw"}}
     [app-form {:*:store      *:store
                :form+        @sub:form+
                :params       {:ok-commands [[:modals/remove! modal-id]]}
                :submit/body  "Save"
                :resource-key new-app-form-key
                :buttons      [[comp/plain-button {:on-click close!}
                                "Cancel"]]}]]
    (finally
      (store/emit! *:store [::forms+/destroyed new-app-form-key]))))

(defmethod ipages/page :routes.ui/applications
  [*:store _]
  [comp/plain-button
   {:*:store  *:store
    :class    ["is-info"]
    :commands [[:modals/create! [::modal]]]}
   "Create application"])
