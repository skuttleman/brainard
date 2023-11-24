(ns brainard.ui.core
  (:require
    [brainard.ui.store.core :as store]
    [clojure.pprint :as pp]
    [reagent.dom :as rdom]))

(defn pprint [data]
  [:pre (with-out-str (pp/pprint data))])

(defn root []
  (let [sub:tags (store/subscribe [::store/tags])
        sub:contexts (store/subscribe [::store/contexts])]
    (fn []
      [:div
       [pprint {:tags     @sub:tags
                :contexts @sub:contexts}]])))

(defn load []
  (rdom/render [root]
               (.getElementById js/document "root")))

(defn init []
  (enable-console-print!)
  (store/dispatch-sync [::store/init])
  (store/dispatch [::store/fetch-tags])
  (store/dispatch [::store/fetch-contexts])
  (load))
