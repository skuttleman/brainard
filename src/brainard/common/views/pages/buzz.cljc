(ns brainard.common.views.pages.buzz
  (:require
    [brainard.common.resources.specs :as rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.pages.interfaces :as ipages]
    [brainard.common.views.pages.shared :as spages]
    [defacto.resources.core :as res]))

(defmethod ipages/page :routes.ui/buzz
  [{:keys [*:store]}]
  (r/with-let [sub:notes (store/subscribe *:store [::res/?:resource ::rspecs/notes#buzz])]
    (let [{:keys [status payload]} @sub:notes]
      [:div
       [:h2.subtitle "What's relevant now?"]
       (if (#{:error :init} status)
         [comp/spinner]
         [spages/search-results payload])])))
