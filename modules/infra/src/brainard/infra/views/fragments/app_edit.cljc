(ns brainard.infra.views.fragments.app-edit
  (:require
    [brainard.api.validations :as valid]
    [brainard.applications.api.specs :as sapps]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [whet.core :as w]
    [whet.utils.reagent :as r]))

(defn ->contact-modal-form-key [form-id modal-id] [::forms+/valid [::contact form-id modal-id]])

(forms+/validated ::contact (valid/->validator sapps/contact)
  [[_ form-id modal-id] {::forms/keys [data] :as spec}]
  {:params      {::w/type ::specs/sub-form
                 :data    data}
   :ok-commands [[:modals/remove! modal-id]]
   :ok-events   [[::forms/modified
                  form-id
                  [:applications/company :companies/contacts]
                  conj]]})

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
                       (ctrls/with-attrs form+ [:applications/details]))]
   [comp/plain-button {:*:store  *:store
                       :commands [[:modals/create! [::contact-modal {:form-id (forms/id form+)}]]]}
    "Add contact"]
   (for [[idx contact] (->> form+
                            forms/data
                            :applications/company
                            :companies/contacts
                            (sort-by :contacts/name)
                            (map-indexed vector))]
     ^{:key idx}
     [comp/pprint contact])])

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
  [_ {:keys [new?]}]
  [:div (if new?
          "Create new application"
          "Edit application")])

(defmethod icomp/modal-body ::modal
  [*:store {:keys [init resource-key] :as attrs}]
  (r/with-let [sub:form+ (store/form+-sub *:store resource-key init {:remove-nil? true})]
    [:div {:style {:min-width "50vw"}}
     [app-form (->create-form-attrs *:store @sub:form+ attrs)]]
    (finally
      (store/emit! *:store [::forms+/destroyed resource-key]))))

(defn ^:private contact-form [*:store form+ form+-key close!]
  [ctrls/form {:*:store      *:store
               :form+        form+
               :resource-key form+-key
               :submit/body  "Add"
               :buttons      [[comp/plain-button {:on-click close! :type :button}
                               "Cancel"]]}
   [ctrls/input (-> {:label       "Name"
                     :auto-focus? true
                     :*:store     *:store}
                    (ctrls/with-attrs form+ [:contacts/name]))]
   [ctrls/input (-> {:label   "Email"
                     :*:store *:store}
                    (ctrls/with-attrs form+ [:contacts/email]))]
   [ctrls/input (-> {:label   "Phone"
                     :*:store *:store}
                    (ctrls/with-attrs form+ [:contacts/phone]))]])

(defmethod icomp/modal-header ::contact-modal
  [_ _]
  [:div "Add contact"])

(defmethod icomp/modal-body ::contact-modal
  [*:store {modal-id :modals/id :modals/keys [close!] :keys [form-id]}]
  (r/with-let [form+-key (->contact-modal-form-key form-id modal-id)
               sub:form+ (store/form+-sub *:store form+-key nil {:remove-nil? true})]
    [contact-form *:store @sub:form+ form+-key close!]
    (finally
      (store/emit! *:store [::forms+/destroyed form+-key]))))
