(ns brainard.common.navigation.core
  (:require
    #?(:cljs [pushy.core :as pushy])
    [bidi.bidi :as bidi]
    [brainard.common.navigation.routing :as routing]
    [brainard.common.stubs.re-frame :as rf]))

(defn ^:private nav-dispatch [route]
  (rf/dispatch [:routing/navigate route]))

(defn ^:private coerce-params [params coercers]
  (reduce (fn [params [k coercer]]
            (cond-> params
              (contains? params k)
              (update k coercer)))
          params
          coercers))

(defn match [path]
  (let [route-info (bidi/match-route routing/all path)
        coercers (routing/handler->coercers (:handler route-info))]
    (update route-info :route-params coerce-params coercers)))

(defonce ^:private link
  #?(:cljs    (doto (pushy/pushy nav-dispatch match)
                pushy/start!)
     :default nil))

(defn path-for
  ([handle]
   (path-for handle nil))
  ([handle params]
   (apply bidi/path-for routing/all handle (flatten (seq (or params {}))))))

(defn goto! [uri]
  #?(:cljs
     (pushy/set-token! link uri)))

(defn navigate!
  ([handle]
   (navigate! handle nil))
  ([handle params]
   (goto! (path-for handle params))))
