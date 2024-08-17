(ns brainard.infra.views.pages.applications.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.applications.api.specs :as sapps]
    [brainard.infra.views.components.core :as comp]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as forms+]
    [brainard.infra.store.specs :as-alias specs]
    [defacto.resources.core :as res]))

(def ^:const new-app-form-key [::forms+/valid [::apps#create]])

(forms+/validated ::apps#create (valid/->validator sapps/create)
  [_ {::forms/keys [data] :as spec}]
  (res/->request-spec [::specs/apps#create] (assoc spec :payload data)))

(defn ->create-form-attrs [*:store form+ {modal-id :modals/id :modals/keys [close!]}]
  {:*:store      *:store
   :form+        form+
   :params       {:ok-commands [[:modals/remove! modal-id]]}
   :submit/body  "Save"
   :resource-key new-app-form-key
   :buttons      [[comp/plain-button {:on-click close!}
                   "Cancel"]]})
