(ns brainard.infra.views.pages.core
  "The core of the UI reagent layout components."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.stubs.reagent :as r]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.resources.core :as res]
    [whet.core :as w]
    [whet.utils.navigation :as nav]
    brainard.infra.views.pages.buzz
    brainard.infra.views.pages.home
    brainard.infra.views.pages.note
    brainard.infra.views.pages.search))

(defmethod ipages/page :default
  [_]
  [:div "Not found"])

(defn ^:private header []
  [:div.layout--row
   [:a {:href "https://disney.fandom.com/wiki/Professor_Brainard" :target "_blank"}
    [:img.logo.layout--space-after {:src "/img/brainard.png" :height "120px" :width "120px"}]]
   [:div.layout--col
    [:h1.title "brainard"]
    [:em "'cause absent-minded people need help 'membering stuff"]]])

(defn ^:private navbar-item [route token & body]
  [:li
   {:class [(when (= token route) "is-active")]}
   (into [:a.navbar-item {:href (nav/path-for rte/all-routes route)}] body)])

(defn ^:private navbar [{:keys [*:store token]}]
  (r/with-let [sub:buzz (store/subscribe *:store [::res/?:resource [::specs/notes#buzz]])]
    (let [resource @sub:buzz
          buzzes (if (res/error? resource)
                   0
                   (count (res/payload resource)))]
      [:nav.navbar
       {:role "navigation" :aria-label "main navigation"}
       [:div.navbar-start.layout--relative
        [:div#header-nav.navbar-menu
         [:ul.navbar-start.oversize.tabs
          [navbar-item :routes.ui/home token
           "Home"]
          [navbar-item :routes.ui/search token
           "Search"]
          [navbar-item :routes.ui/buzz token
           "Buzz"
           (when (pos? buzzes)
             [:span.tag.is-info.space--left buzzes])]]]]])))

(defn page [{:keys [*:store] :as attrs}]
  [:div.container
   [header]
   [navbar attrs]
   [ipages/page attrs]
   [comp/toasts *:store]
   [comp/modals *:store]])

(defn root [*:store]
  (r/with-let [router (store/subscribe *:store [::w/?:route])]
    [page (assoc @router :*:store *:store)]))
