(ns brainard.infra.views.pages.trash.actions
  (:require
   [brainard.infra.store.specs :as-alias specs]
   [defacto.forms.core :as-alias forms]
   [defacto.resources.core :as res]))

(def sync-key [::notes#sync])

(defmethod res/->request-spec ::notes#sync
  [_ spec]
  (case (::action spec)
    ::fetch (res/->request-spec [::specs/notes#select] spec)
    ::bulk-delete (res/->request-spec [::specs/notes#bulk-delete] spec)))
