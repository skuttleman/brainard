(ns brainard.infra.utils.routing
  "Tokenized HTTP routing table for use with [[bidi.bidi]]"
  (:require
    [brainard.api.utils.uuids :as uuids]
    [whet.interfaces :as iwhet])
  #?(:clj
     (:import
       (java.net URLEncoder URLDecoder))))

(def ^:private token->coercers
  {:routes.api/note     {:notes/id uuids/->uuid}
   :routes.ui/note      {:notes/id uuids/->uuid}
   :routes.api/schedule {:schedules/id uuids/->uuid}})

(defn ^:private coerce-params [token params]
  (let [coercers (token->coercers token)]
    (reduce (fn [params [k coercer]]
              (cond-> params
                (contains? params k)
                (update k coercer)))
            params
            coercers)))

(defmethod iwhet/coerce-route-params :routes.api/note [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.ui/note [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.api/schedule [token params] (coerce-params token params))

(def ^:private api-routes
  ["/api" [["/notes" [["" :routes.api/notes]
                      [["/" [uuids/regex :notes/id]] :routes.api/note]
                      ["/scheduled" :routes.api/notes?scheduled]]]
           ["/schedules" [["" :routes.api/schedules]
                          [["/" [uuids/regex :schedules/id]] :routes.api/schedule]]]
           ["/tags" :routes.api/tags]
           ["/contexts" :routes.api/contexts]
           [true :routes.api/not-found]]])

(def ^:private resource-routes
  ["" [["/js/" [[true :routes.resources/js]]]
       ["/css/" [[true :routes.resources/css]]]
       ["/img/" [[true :routes.resources/img]]]
       ["/favicon.ico" :routes.resources/not-found]]])

(def ^:private ui-routes
  ["" [["/" :routes.ui/workspace]
       ["/note" :routes.ui/main]
       ["/buzz" :routes.ui/buzz]
       ["/search" :routes.ui/search]
       [["/notes/" [uuids/regex :notes/id]] :routes.ui/note]
       [true :routes.ui/not-found]]])

(def all-routes
  ["" [api-routes resource-routes ui-routes]])
