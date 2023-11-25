(ns brainard.ui.navigation
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.routing :as routing]
    [brainard.ui.store.core :as store]
    [pushy.core :as pushy]))

(defn ^:private dispatch [route]
  (store/dispatch [:routing/navigate route]))

(defn ^:private match [path]
  (bidi/match-route routing/all path))

(defonce ^:private link
  (doto (pushy/pushy dispatch match)
    pushy/start!))

(defn path-for
  ([handle]
   (path-for handle nil))
  ([handle params]
   (bidi/path-for routing/all handle (or params {}))))

(defn goto! [uri]
  (pushy/set-token! link uri))

(defn navigate!
  ([handle]
   (navigate! handle nil))
  ([handle params]
   (goto! (path-for handle params))))
