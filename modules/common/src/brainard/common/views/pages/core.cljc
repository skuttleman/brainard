(ns brainard.common.views.pages.core
  "The core of the UI reagent layout components."
  (:require
    [brainard.common.services.navigation.core :as nav]
    [brainard.common.services.store.core :as store]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.pages.interfaces :as ipages]
    brainard.common.views.pages.home
    brainard.common.views.pages.notes
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

(defn ^:private navbar [{:keys [handler]}]
  (let [home (nav/path-for :routes.ui/home)
        search (nav/path-for :routes.ui/search)]
    [:nav.navbar
     {:role "navigation" :aria-label "main navigation"}
     [:div.navbar-start
      {:style {:position :relative}}
      [:div#header-nav.navbar-menu
       [:ul.navbar-start.oversize.tabs
        [:li
         {:class [(when (= :routes.ui/home handler) "is-active")]}
         [:a.navbar-item {:href home} "Home"]]
        [:li
         {:class [(when (= :routes.ui/search handler) "is-active")]}
         [:a.navbar-item {:href search} "Search"]]]]]]))

(defn page [route-info]
  [ipages/page route-info])

(defn root []
  (let [router (store/subscribe [:routing/route])]
    (fn []
      (let [route @router]
        [:div.container
         [header]
         [navbar route]
         [page route]
         [comp/toasts]]))))
