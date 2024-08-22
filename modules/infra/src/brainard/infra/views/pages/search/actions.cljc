(ns brainard.infra.views.pages.search.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.notes.api.specs :as snotes]
    [defacto.forms.core :as forms]
    [brainard.infra.store.specs :as-alias specs]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(defmethod forms+/re-init ::search [_ form _] (forms/data form))
(forms+/validated ::search (valid/->validator snotes/query)
  [_ {::forms/keys [data] :as spec}]
  (res/->request-spec [::specs/notes#select] (assoc spec :params data)))
