(ns brainard.infra.views.pages.applications.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.applications.api.specs :as sapps]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.fragments.app-edit :as app-edit]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(def ^:const new-app-form-key [::forms+/valid [::apps#create]])

(def ^:private ^:const init-app
  {:applications/company {::present? true}})

(forms+/validated ::apps#create (valid/->validator sapps/create)
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload data)]
    (specs/with-cbs (res/->request-spec [::specs/apps#create] spec)
                    :err-commands [[:toasts/fail!]]
                    :ok-commands [[:toasts.applications/succeed!]])))

(defn ->new-app-modal-button-attrs [*:store]
  {:*:store  *:store
   :class    ["is-info"]
   :commands [[:modals/create! [::app-edit/modal {:init         init-app
                                                  :new?         true
                                                  :resource-key new-app-form-key}]]]})
