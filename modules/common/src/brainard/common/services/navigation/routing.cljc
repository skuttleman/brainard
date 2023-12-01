(ns brainard.common.services.navigation.routing
  "Tokenized HTTP routing table for use with [[bidi.bidi]]"
  (:require
    [brainard.common.utils.uuids :as uuids]))

(def ^:private api-routes
  ["/api" [["/notes" [["" :routes.api/notes]
                      [["/" [uuids/regex :notes/id]] :routes.api/note]]]
           ["/tags" :routes.api/tags]
           ["/contexts" :routes.api/contexts]
           [true :routes.api/not-found]]])

(def ^:private resource-routes
  ["" [["/js/" [[true :routes.resources/js]]]
       ["/css/" [[true :routes.resources/css]]]
       ["/img/" [[true :routes.resources/img]]]]])

(def ^:private ui-routes
  ["" [["/" :routes.ui/home]
       ["/search" :routes.ui/search]
       [["/notes/" [uuids/regex :notes/id]] :routes.ui/note]
       [true :routes.ui/not-found]]])

(def handler->coercers
  {:routes.api/note {:notes/id uuids/->uuid}
   :routes.ui/note  {:notes/id uuids/->uuid}})

(def all
  ["" [api-routes resource-routes ui-routes]])
