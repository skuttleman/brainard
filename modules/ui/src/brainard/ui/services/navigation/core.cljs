(ns brainard.ui.services.navigation.core
  (:require
    [brainard.common.utils.routing :as rte]
    [pushy.core :as pushy]))

(def pushy-link)

(defn navigate!
  "Navigates with history to a URI (string) or `handle` (keyword) with option `params`."
  ([handle-or-uri]
   (if (string? handle-or-uri)
     (pushy/set-token! pushy-link handle-or-uri)
     (navigate! handle-or-uri nil)))
  ([handle params]
   (pushy/set-token! pushy-link (rte/path-for handle params))))

(defn replace!
  "Generates a routing uri and navigates via [[replace-uri!]]."
  ([handle-or-uri]
   (if (string? handle-or-uri)
     (pushy/replace-token! pushy-link handle-or-uri)
     (replace! handle-or-uri nil)))
  ([handle params]
   (pushy/replace-token! pushy-link (rte/path-for handle params))))
