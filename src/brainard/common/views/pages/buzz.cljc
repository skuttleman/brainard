(ns brainard.common.views.pages.buzz
  (:require
    [brainard.common.resources.specs :as rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.pages.interfaces :as ipages]
    [brainard.common.views.pages.shared :as spages]))

(defn ^:private buzz [[notes]]
  [spages/search-results notes])

(defmethod ipages/page :routes.ui/buzz
  [{:keys [*:store]}]
  (r/with-let [sub:notes (store/subscribe *:store [:resources/?:resource ::rspecs/notes#poll])]
    [:div
     [:h2.subtitle "What's relevant now?"]
     [comp/with-resources [sub:notes] buzz]]))
