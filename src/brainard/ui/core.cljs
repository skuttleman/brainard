(ns brainard.ui.core
  (:require
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.views.pages.home :as pages.home]
    [brainard.common.views.pages.search :as pages.search]
    [brainard.ui.services.navigation :as nav]
    [clojure.pprint :as pp]
    [reagent.dom :as rdom]
    brainard.ui.services.store.core))

(defn ^:private not-found [_]
  [:div "Not found"])

(def ^:private pages
  {:routes.ui/home      pages.home/root
   :routes.ui/search    pages.search/root
   :routes.ui/not-found not-found})

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
  (let [router (rf/subscribe [:routing/route])]
    (fn []
      (let [route @router
            page (get pages (:handler route) not-found)]
        [:div.container
         [navbar route]
         [page route]
         [pprint @re-frame.db/app-db]]))))

(defn load []
  (rdom/render [root]
               (.getElementById js/document "root")))

(defn init []
  (enable-console-print!)
  (rf/dispatch-sync [:core/init])
  (rf/dispatch [:api.tags/fetch])
  (rf/dispatch [:api.contexts/fetch])
  (load))
