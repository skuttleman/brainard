(ns brainard.common.views.pages.core
  (:require
    [brainard.common.navigation.core :as nav]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.views.main :as views.main]
    [brainard.common.views.pages.home :as pages.home]
    [brainard.common.views.pages.search :as pages.search]
    [brainard.common.views.pages.notes :as pages.notes]
    [brainard.common.views.toasts :as toasts]))

(defn ^:private not-found [_]
  [:div "Not found"])

(def ^:private pages
  {:routes.ui/home      pages.home/root
   :routes.ui/search    pages.search/root
   :routes.ui/note      pages.notes/root
   :routes.ui/not-found not-found})

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

(defn root []
  (let [router (rf/subscribe [:routing/route])]
    (fn []
      (let [route @router
            page (get pages (:handler route) not-found)]
        [:div.container
         [navbar route]
         [page route]
         [views.main/pprint @re-frame.db/app-db]
         [toasts/toasts]]))))
