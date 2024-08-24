(ns brainard.infra.views.pages.application.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.applications.api.specs :as sapps]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.fragments.app-edit :as app-edit]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(def ^:const edit-app-form-key [::forms+/valid [::apps#modify]])

(defn ^:private ->init [app]
  (assoc-in app [:applications/company ::present?] true))

(forms+/validated ::apps#modify (valid/->validator sapps/modify)
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload data)
        app-id (:applications/id data)]
    (specs/with-cbs (res/->request-spec [::specs/apps#modify app-id] spec)
                    :ok-commands [[::res/submit! [::specs/apps#find app-id]]]
                    :err-commands [[:toasts/fail!]])))

(defn ->edit-app-modal-button-attrs [*:store app]
  {:*:store  *:store
   :class    ["is-info"]
   :commands [[:modals/create! [::app-edit/modal {:init         (->init app)
                                                  :resource-key edit-app-form-key}]]]})
