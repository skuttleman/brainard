(ns brainard.infra.views.fragments.actions
  (:require
   [brainard.api.validations :as valid]
   [brainard.attachments.api.specs :as sattachments]
   [brainard.infra.store.specs :as-alias specs]
   [brainard.notes.api.specs :as snotes]
   [clojure.core.async :as async]
   [clojure.string :as string]
   [defacto.forms.core :as forms]
   [defacto.forms.plus :as forms+]
   [defacto.resources.core :as res]
   [whet.core :as-alias w]
   [whet.interfaces :as iwhet]))

(def attachment-form-key [::forms+/valid [::modal ::attachment-edit]])
(def todo-form-key [::forms+/valid [::modal ::todo-edit]])
(def link-search-key [::notes#search ::links])

(def ^:private attachment-validator (valid/->validator sattachments/modify))
(def ^:private todo-validator (valid/->validator snotes/todo-create))

(defmethod forms+/re-init ::modal
  [_ _ resource-payload]
  (dissoc resource-payload :todos/text :attachments/name))

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

(defmethod res/->request-spec ::notes#search
  [_ text]
  (let [body (string/trim text)]
    (when (seq body)
      (res/->request-spec [::specs/notes#select] {:params {:notes/body body :notes/summarize true}}))))
