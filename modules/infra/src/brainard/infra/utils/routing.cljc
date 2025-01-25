(ns brainard.infra.utils.routing
  "Tokenized HTTP routing table for use with [[bidi.bidi]]"
  (:require
    [brainard.api.utils.uuids :as uuids]
    [whet.interfaces :as iwhet]))

(def ^:private token->coercers
  {:routes.api/note             {:notes/id uuids/->uuid}
   :routes.api/note?history     {:notes/id uuids/->uuid}
   :routes.api/schedule         {:schedules/id uuids/->uuid}
   :routes.api/workspace-node   {:workspace-nodes/id uuids/->uuid}
   :routes.resources/attachment {:attachments/id uuids/->uuid}
   :routes.ui/note              {:notes/id uuids/->uuid}})

(defn ^:private coerce-params [token params]
  (let [coercers (token->coercers token)]
    (reduce (fn [params [k coercer]]
              (cond-> params
                (contains? params k)
                (update k coercer)))
            params
            coercers)))

(doseq [token (keys token->coercers)]
  (defmethod iwhet/coerce-route-params token [token params] (coerce-params token params)))

(def ^:private api-routes
  ["/api" [["/notes" [["" :routes.api/notes]
                      [["/" [uuids/regex :notes/id]] [["" :routes.api/note]
                                                      ["/history" :routes.api/note?history]]]
                      ["/scheduled" :routes.api/notes?scheduled]]]
           ["/schedules" [["" :routes.api/schedules]
                          [["/" [uuids/regex :schedules/id]] :routes.api/schedule]]]
           ["/workspace-nodes" [["" :routes.api/workspace-nodes]
                                [["/" [uuids/regex :workspace-nodes/id]] :routes.api/workspace-node]]]
           ["/attachments" :routes.api/attachments]
           ["/tags" :routes.api/tags]
           ["/contexts" :routes.api/contexts]
           [true :routes.api/not-found]]])

(def ^:private resource-routes
  ["" [[["/attachments/" [uuids/regex :attachments/id]] :routes.resources/attachment]
       ["/css/" [[true :routes.resources/css]]]
       ["/img/" [[true :routes.resources/img]]]
       ["/js/" [[true :routes.resources/js]]]
       ["/favicon.ico" :routes.resources/not-found]]])

(def ^:private ui-routes
  ["" [["/" :routes.ui/home]
       ["/buzz" :routes.ui/buzz]
       [["/notes/" [uuids/regex :notes/id]] :routes.ui/note]
       ["/search" :routes.ui/search]
       [true :routes.ui/not-found]]])

(def all-routes
  ["" [api-routes resource-routes ui-routes]])
