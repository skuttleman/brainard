(ns brainard.ui.services.navigation.core
  (:require
    [brainard.common.services.store.core :as store]
    [brainard.common.utils.routing :as rte]
    [pushy.core :as pushy]))

(defn ^:private nav-dispatch [route]
  (store/dispatch [:routing/navigate route]))

(defonce ^:private link
         (doto (pushy/pushy nav-dispatch rte/match)
           pushy/start!))

(defn navigate!
  "Navigates with history to a URI (string) or `handle` (keyword) with option `params`."
  ([handle-or-uri]
   (if (string? handle-or-uri)
     (pushy/set-token! link handle-or-uri)
     (navigate! handle-or-uri nil)))
  ([handle params]
   (pushy/set-token! link (rte/path-for handle params))))

(defn replace!
  "Generates a routing uri and navigates via [[replace-uri!]]."
  ([handle-or-uri]
   (if (string? handle-or-uri)
     (pushy/replace-token! link handle-or-uri)
     (replace! handle-or-uri nil)))
  ([handle params]
   (pushy/replace-token! link (rte/path-for handle params))))
