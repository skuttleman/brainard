(ns brainard.common.views.pages.core
  "The core of the UI reagent layout components."
  (:require
    [brainard.common.store.core :as store]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.pages.interfaces :as ipages]
    brainard.common.views.pages.home
    brainard.common.views.pages.note
    brainard.common.views.pages.search))

(defmethod ipages/page :default
  [_]
  [:div "Not found"])

(defn ^:private header []
  [:div.layout--row
   [:a {:href "https://disney.fandom.com/wiki/Professor_Brainard" :target "_blank"}
    [:img.logo.layout--space-after {:src "/img/brainard.png" :height "120px" :width "120px"}]]
   [:div.layout--col
    [:h1.title "brainard"]
    [:em "'cause absent-minded people need help 'membering junk"]]])

(defn ^:private navbar [{:keys [token]}]
  (let [home (rte/path-for :routes.ui/home)
        search (rte/path-for :routes.ui/search)]
    [:nav.navbar
     {:role "navigation" :aria-label "main navigation"}
     [:div.navbar-start.layout--relative
      [:div#header-nav.navbar-menu
       [:ul.navbar-start.oversize.tabs
        [:li
         {:class [(when (= :routes.ui/home token) "is-active")]}
         [:a.navbar-item {:href home} "Home"]]
        [:li
         {:class [(when (= :routes.ui/search token) "is-active")]}
         [:a.navbar-item {:href search} "Search"]]]]]]))

(defn page [*:store route-info]
  [:div.container
   [header]
   [navbar route-info]
   [ipages/page (assoc route-info :*:store *:store)]
   [comp/toasts *:store]
   [comp/modals *:store]])

(defn root [*:store]
  (r/with-let [router (store/subscribe *:store [:routing/?:route])]
    [page *:store @router]))
