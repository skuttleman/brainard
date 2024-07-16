(ns brainard.infra.views.pages.application
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private application [{:applications/keys [company] :as app}]
  [:div
   [:h2.subtitle "Application Details"]
   [:div.layout--row
    [:strong.layout--space-after "Application status:"]
    [:span (name (:applications/state app))]]
   [:div.layout--row
    [:strong.layout--space-after "Company name:"]
    [:span (:companies/name company)]]
   (when-let [website (:companies/website company)]
     [:div.layout--row
      [:strong.layout--space-after "Company website:"]
      [:a.link {:href website :target "_blank"} (:companies/name company)]])
   (when-let [location (:companies/location company)]
     [:div.layout--row
      [:strong.layout--space-after "Company location:"]
      [:span location]])])

(defn ^:private root [*:store app]
  [:div
   [application app]])

(defmethod ipages/page :routes.ui/application
  [*:store {:keys [route-params]}]
  (r/with-let [app-id (:applications/id route-params)
               sub:app (-> *:store
                           (store/dispatch! [::res/ensure! [::specs/apps#find app-id]])
                           (store/subscribe [::res/?:resource [::specs/apps#find app-id]]))]
    [comp/with-resource sub:app [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/apps#find app-id]]))))
