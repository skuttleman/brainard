(ns brainard.ui.core
  (:require
    [brainard.common.views.pages.core :as pages]
    [brainard.ui.navigation :as nav]
    [brainard.ui.store.core :as store]
    [clojure.pprint :as pp]
    [reagent.dom :as rdom]))

(defn pprint [data]
  [:pre (with-out-str (pp/pprint data))])

(defn navbar [{:keys [handler]}]
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
  (let [router (store/subscribe [:routing/route])]
    (fn []
      (let [route @router]
        [:div
         [navbar route]
         [pages/page route]
         [pprint @re-frame.db/app-db]]))))

(defn load []
  (rdom/render [root]
               (.getElementById js/document "root")))

(defn init []
  (enable-console-print!)
  (store/dispatch-sync [:core/init])
  (store/dispatch [:api.tags/fetch])
  (store/dispatch [:api.contexts/fetch])
  (load))
