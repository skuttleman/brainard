(ns brainard.infra.views.fragments.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.attachments.api.specs :as sattachments]
    [brainard.notes.api.specs :as snotes]
    [clojure.core.async :as async]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.core :as-alias w]
    [whet.interfaces :as iwhet]))

(def attachment-form-key [::forms+/valid [::modal ::attachment-edit]])
(def todo-form-key [::forms+/valid [::modal ::todo-edit]])

(def ^:private attachment-validator (valid/->validator sattachments/modify))
(def ^:private todo-validator (valid/->validator snotes/todo-create))

(defmethod forms+/validate ::modal
  [[_ form-type] form-data]
  (case form-type
    ::attachment-edit (attachment-validator form-data)
    ::todo-edit (todo-validator form-data)))

(defmethod res/->request-spec ::modal
  [_ spec]
  (assoc spec :params {::w/type     ::local
                       ::forms/form (::forms/form spec)}))

(defmethod iwhet/handle-request ::local
  [_ _ {::forms/keys [form]}]
  (async/go
    [::res/ok (forms/data form)]))
