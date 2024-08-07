(ns brainard.infra.utils.routing
  "Tokenized HTTP routing table for use with [[bidi.bidi]]"
  (:require
    [brainard.api.utils.uuids :as uuids]
    [whet.interfaces :as iwhet]))

(def ^:private token->coercers
  {:routes.api/note           {:notes/id uuids/->uuid}
   :routes.api/note?history   {:notes/id uuids/->uuid}
   :routes.ui/note            {:notes/id uuids/->uuid}
   :routes.api/schedule       {:schedules/id uuids/->uuid}
   :routes.api/workspace-node {:workspace-nodes/id uuids/->uuid}
   :routes.api/application    {:applications/id uuids/->uuid}
   :routes.ui/application     {:applications/id uuids/->uuid}})

(defn ^:private coerce-params [token params]
  (let [coercers (token->coercers token)]
    (reduce (fn [params [k coercer]]
              (cond-> params
                (contains? params k)
                (update k coercer)))
            params
            coercers)))

(defmethod iwhet/coerce-route-params :routes.api/note [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.api/note?history [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.ui/note [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.api/schedule [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.api/workspace-node [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.api/application [token params] (coerce-params token params))
(defmethod iwhet/coerce-route-params :routes.ui/application [token params] (coerce-params token params))

(def ^:private api-routes
  ["/api" [["/notes" [["" :routes.api/notes]
                      [["/" [uuids/regex :notes/id]] [["" :routes.api/note]
                                                      ["/history" :routes.api/note?history]]]
                      ["/scheduled" :routes.api/notes?scheduled]]]
           ["/schedules" [["" :routes.api/schedules]
                          [["/" [uuids/regex :schedules/id]] :routes.api/schedule]]]
           ["/workspace-nodes" [["" :routes.api/workspace-nodes]
                                [["/" [uuids/regex :workspace-nodes/id]] :routes.api/workspace-node]]]
           ["/applications" [["" :routes.api/applications]
                             [["/" [uuids/regex :applications/id]] :routes.api/application]]]
           ["/tags" :routes.api/tags]
           ["/contexts" :routes.api/contexts]
           [true :routes.api/not-found]]])

(def ^:private resource-routes
  ["" [["/js/" [[true :routes.resources/js]]]
       ["/css/" [[true :routes.resources/css]]]
       ["/img/" [[true :routes.resources/img]]]
       ["/favicon.ico" :routes.resources/not-found]]])

(def ^:private ui-routes
  ["" [["/" :routes.ui/home]
       ["/buzz" :routes.ui/buzz]
       [["/notes/" [uuids/regex :notes/id]] :routes.ui/note]
       ["/search" :routes.ui/search]
       ["/applications" [["" :routes.ui/applications]
                         [["/" [uuids/regex :applications/id]] :routes.ui/application]]]
       [true :routes.ui/not-found]]])

(def all-routes
  ["" [api-routes resource-routes ui-routes]])
