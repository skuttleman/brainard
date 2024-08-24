(ns brainard.infra.views.fragments.app-edit
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [defacto.forms.plus :as-alias forms+]
    [whet.utils.reagent :as r]))

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

(defn ^:private ->create-form-attrs [*:store form+ attrs]
  (let [{modal-id :modals/id :modals/keys [close!] :keys [resource-key]} attrs]
    {:*:store      *:store
     :form+        form+
     :params       {:ok-commands [[:modals/remove! modal-id]]}
     :submit/body  "Save"
     :resource-key resource-key
     :buttons      [[comp/plain-button {:on-click close!}
                     "Cancel"]]}))

(defmethod icomp/modal-header ::modal
  [_ _]
  [:div "Create new application"])

(defmethod icomp/modal-body ::modal
  [*:store {:keys [init resource-key] :as attrs}]
  (r/with-let [sub:form+ (store/form+-sub *:store resource-key init {:remove-nil? true})]
    [:div {:style {:min-width "50vw"}}
     [app-form (->create-form-attrs *:store @sub:form+ attrs)]]
    (finally
      (store/emit! *:store [::forms+/destroyed resource-key]))))
