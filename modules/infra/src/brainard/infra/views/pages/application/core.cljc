(ns brainard.infra.views.pages.application.core
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private details [{:applications/keys [company] :as app}]
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
      [comp/link {:href website :target "_blank"}
       (:companies/name company)]])
   (when-let [location (:companies/location company)]
     [:div.layout--row
      [:strong.layout--space-after "Company location:"]
      [:span location]])
   (when-let [details (:applications/details app)]
     [:div.layout--col
      [:strong.layout--space-after "Application details:"]
      [:div.layout--indent
       [:p details]]])])

(defn ^:private root [*:store app]
  [:div
   [details app]
   [:div.button-row]])

(defmethod ipages/page :routes.ui/application
  [*:store {:keys [route-params]}]
  (r/with-let [app-id (:applications/id route-params)
               sub:app (store/res-sub *:store [::specs/apps#find app-id])]
    [comp/with-resource sub:app [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/apps#find app-id]]))))
