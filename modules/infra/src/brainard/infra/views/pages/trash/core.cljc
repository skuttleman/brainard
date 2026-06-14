(ns brainard.infra.views.pages.trash.core
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.store.specs :as-alias specs]
   [brainard.infra.views.components.core :as comp]
   [brainard.infra.views.pages.interfaces :as ipages]
   [brainard.notes.infra.views :as notes.views]
   [defacto.resources.core :as-alias res]
   [whet.utils.reagent :as r]))

(def ^:private archived-res
  [::specs/notes#select ::archived])

(defmethod ipages/page :routes.ui/trash
  [*:store _]
  (r/with-let [sub:res (store/res-sub *:store archived-res {:params {:archived :only}})]
    [:div
     [:h2.subtitle "Archived notes"]
     [comp/with-resource sub:res [notes.views/note-list {:*:store *:store}]]]
    (finally
      (store/emit! *:store [::res/destroyed archived-res]))))
