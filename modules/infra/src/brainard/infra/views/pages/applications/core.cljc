(ns brainard.infra.views.pages.applications.core
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.applications.actions :as apps.act]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private app-list [*:store apps]
  [:ul.applications.layout--stack-between
   (for [{app-id :applications/id :as app} apps]
     ^{:key app-id}
     [:li.layout--space-between
      [:div.layout--room-between
       [:span
        (-> app :applications/company :companies/name)]
       [:span
        (-> app :applications/state name)]]
      [comp/link {:class        ["button" "is-link" "is-small"]
                  :token        :routes.ui/application
                  :route-params {:applications/id app-id}}
       "view"]])])

(defn ^:private root [*:store apps]
  [:div.layout--stack-between
   [:div
    [comp/plain-button
     (apps.act/->new-app-modal-button-attrs *:store)
     "New application"]]
   (when (seq apps)
     [app-list *:store apps])])

(defmethod ipages/page :routes.ui/applications
  [*:store _]
  (r/with-let [sub:apps (store/res-sub *:store [::specs/apps#select])]
    [comp/with-resource sub:apps [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/apps#select]]))))
