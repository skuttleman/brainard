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
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(def ^:private ^:const new-app-form-key
  [::forms+/valid [::specs/apps#new]])

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

(defn ^:private app-list [*:store apps]
  [:ul.applications.layout--stack-between
   (for [{app-id :applications/id :as app} apps]
     ^{:key app-id}
     [:li.layout--space-between
      [:div.layout--room-between
       [:span
        (-> app :applications/company :companies/name)]
       [:span
        (-> app :applications/state name)]]
      [comp/link {:class        ["button" "is-link" "is-small"]
                  :token        :routes.ui/application
                  :route-params {:applications/id app-id}}
       "view"]])])

(defn ^:private root [*:store apps]
  [:div.layout--stack-between
   [:div
    [comp/plain-button
     {:*:store  *:store
      :class    ["is-info"]
      :commands [[:modals/create! [::modal]]]}
     "New application"]]
   (when (seq apps)
     [app-list *:store apps])])

(defmethod ipages/page :routes.ui/applications
  [*:store _]
  (r/with-let [sub:apps (-> *:store
                            (store/dispatch! [::res/ensure! [::specs/apps#select]])
                            (store/subscribe [::res/?:resource [::specs/apps#select]]))]
    [comp/with-resource sub:apps [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/apps#select]]))))
