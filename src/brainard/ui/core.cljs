(ns brainard.ui.core
  (:require
    [brainard.ui.store.core :as store]
    [clojure.pprint :as pp]
    [reagent.dom :as rdom]))

(defn pprint [data]
  [:pre (with-out-str (pp/pprint data))])

(defn root []
  (let [sub:form (store/subscribe [:forms/value 123])
        sub:errors (store/subscribe [:forms/errors 123])]
    (fn []
      [:div
       [pprint
        [@re-frame.db/app-db
         {:form   @sub:form
          :errors @sub:errors}]]])))

(defn load []
  (rdom/render [root]
               (.getElementById js/document "root")))

(defn init []
  (enable-console-print!)
  (store/dispatch-sync [:core/init])
  (store/dispatch [:api.tags/fetch])
  (store/dispatch [:api.contexts/fetch])
  (load))
