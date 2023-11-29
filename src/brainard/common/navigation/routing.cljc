(ns brainard.common.navigation.routing
  (:require
    [brainard.common.utils.uuids :as uuids]))

(def api-routes
  ["/api"
   [["/notes"
     [["" :routes.api/notes]
      [["/" [uuids/regex :notes/id]] :routes.api/note]]]
    ["/tags"
     [["" :routes.api/tags]]]
    ["/contexts"
     [["" :routes.api/contexts]]]
    [true :routes.api/not-found]]])

(def resource-routes
  [""
   [["/js/" [[true :routes.resources/js]]]
    ["/css/" [[true :routes.resources/css]]]]])

(def ui-routes
  [""
   [["/" :routes.ui/home]
    ["/search" :routes.ui/search]
    [true :routes.ui/not-found]]])

(def all
  [""
   [api-routes
    resource-routes
    ui-routes]])
