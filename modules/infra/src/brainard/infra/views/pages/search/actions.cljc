(ns brainard.infra.views.pages.search.actions
  (:require
   [brainard.api.validations :as valid]
   [brainard.notes.api.specs :as snotes]
   [defacto.forms.core :as forms]
   [brainard.infra.store.specs :as-alias specs]
   [defacto.forms.plus :as forms+]
   [defacto.resources.core :as res]))

(defn ^:private convert-archived [data]
  (update data :notes/archived #(when % :both)))

(defmethod forms+/re-init ::search [_ form _] (forms/data form))
(forms+/validated ::search (comp (valid/->validator snotes/query) convert-archived)
  [_ {::forms/keys [data] :as spec}]
  (let [params (-> data
                   convert-archived
                   (valid/select-spec-keys snotes/query))]
    (res/->request-spec [::specs/notes#select] (assoc spec :params params))))
