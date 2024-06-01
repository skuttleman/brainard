(ns brainard.infra.views.pages.pinned
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private root [*store route-info [notes]]
  [notes.views/search-results route-info notes])

(defmethod ipages/page :routes.ui/pinned
  [*:store route-info]
  (r/with-let [sub:pinned (do (store/dispatch! *:store [::res/ensure! [::specs/notes#pinned]])
                            (store/subscribe *:store [::res/?:resource [::specs/notes#pinned]]))]
    [comp/with-resources [sub:pinned] [root *:store route-info]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/notes#pinned]]))))
