(ns brainard.common.stubs.nav
  (:require
    #?(:cljs [pushy.core :as pushy])
    [brainard.common.utils.routing :as rte]))

(defn navigate!
  "Navigates with history to a URI (string) or `handle` (keyword) with option `params`."
  ([nav handle-or-uri]
   #?(:cljs
      (if (string? handle-or-uri)
        (pushy/set-token! nav handle-or-uri)
        (navigate! nav handle-or-uri nil))))
  ([nav handle params]
   #?(:cljs
      (pushy/set-token! nav (rte/path-for handle params)))))

(defn replace!
  "Generates a routing uri and navigates via [[replace-uri!]]."
  ([nav handle-or-uri]
   #?(:cljs
      (if (string? handle-or-uri)
        (pushy/replace-token! nav handle-or-uri)
        (replace! nav handle-or-uri nil))))
  ([nav handle params]
   #?(:cljs
      (pushy/replace-token! nav (rte/path-for handle params)))))
