(ns brainard.infra.views.pages.applications.actions
  (:require
    [brainard.infra.views.components.core :as comp]
    [defacto.forms.plus :as-alias forms+]
    [brainard.infra.store.specs :as-alias specs]))

(def ^:const new-app-form-key [::forms+/valid [::specs/apps#create]])

(defn ->create-form-attrs [*:store form+ {modal-id :modals/id :modals/keys [close!]}]
  {:*:store      *:store
   :form+        form+
   :params       {:ok-commands [[:modals/remove! modal-id]]}
   :submit/body  "Save"
   :resource-key new-app-form-key
   :buttons      [[comp/plain-button {:on-click close!}
                   "Cancel"]]})
