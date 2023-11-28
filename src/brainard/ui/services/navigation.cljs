(ns brainard.ui.services.navigation
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.routing :as routing]
    [brainard.common.stubs.re-frame :as rf]
    [pushy.core :as pushy]))

(defn ^:private dispatch [route]
  (rf/dispatch [:routing/navigate route]))

(defn ^:private match [path]
  (-> routing/all
      (bidi/match-route path)
      (update :handler keyword)))

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
