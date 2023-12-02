(ns brainard.ui.services.navigation.core
  (:require
    [brainard.common.services.store.core :as store]
    [brainard.common.utils.routing :as rte]
    [pushy.core :as pushy]))

(defonce ^:private pushy
  (letfn [(dispatch [route]
            (store/dispatch [:routing/navigate route]))]
    (doto (pushy/pushy dispatch rte/match)
      pushy/start!)))

(defn navigate!
  "Navigates with history to a URI (string) or `handle` (keyword) with option `params`."
  ([handle-or-uri]
   (if (string? handle-or-uri)
     (pushy/set-token! pushy handle-or-uri)
     (navigate! handle-or-uri nil)))
  ([handle params]
   (pushy/set-token! pushy (rte/path-for handle params))))

(defn replace!
  "Generates a routing uri and navigates via [[replace-uri!]]."
  ([handle-or-uri]
   (if (string? handle-or-uri)
     (pushy/replace-token! pushy handle-or-uri)
     (replace! handle-or-uri nil)))
  ([handle params]
   (pushy/replace-token! pushy (rte/path-for handle params))))
