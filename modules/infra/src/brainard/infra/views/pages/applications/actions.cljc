(ns brainard.infra.views.pages.applications.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.applications.api.specs :as sapps]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(def ^:const new-app-form-key [::forms+/valid [::apps#create]])

(forms+/validated ::apps#create (valid/->validator sapps/create)
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload data)]
    (specs/with-cbs (res/->request-spec [::specs/apps#create] spec)
                    :err-commands [[:toasts/fail!]]
                    :ok-commands [[:toasts.applications/succeed!]])))

(defn ->create-form-attrs [*:store form+ {modal-id :modals/id :modals/keys [close!]}]
  {:*:store      *:store
   :form+        form+
   :params       {:ok-commands [[:modals/remove! modal-id]]}
   :submit/body  "Save"
   :resource-key new-app-form-key
   :buttons      [[comp/plain-button {:on-click close!}
                   "Cancel"]]})
