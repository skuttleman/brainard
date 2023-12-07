(ns brainard.common.stubs.nav
  (:require
    #?(:cljs [pushy.core :as pushy])
    [brainard.common.utils.routing :as rte]))

(def ^:dynamic *nav*)

(defn navigate!
  "Navigates with history to a URI (string) or `handle` (keyword) with option `params`."
  ([handle-or-uri]
   #?(:cljs
      (if (string? handle-or-uri)
        (pushy/set-token! *nav* handle-or-uri)
        (navigate! handle-or-uri nil))))
  ([handle params]
   #?(:cljs
      (pushy/set-token! *nav* (rte/path-for handle params)))))

(defn replace!
  "Generates a routing uri and navigates via [[replace-uri!]]."
  ([handle-or-uri]
   #?(:cljs
      (if (string? handle-or-uri)
        (pushy/replace-token! *nav* handle-or-uri)
        (replace! handle-or-uri nil))))
  ([handle params]
   #?(:cljs
      (pushy/replace-token! *nav* (rte/path-for handle params)))))
