(ns brainard.infra.routes.table)

(def api-routes
  ["/api"
   [["/notes"
     [["" :routes.api/notes]]]
    ["/tags"
     [["" :routes.api/tags]]]
    ["/contexts"
     [["" :routes.api/contexts]]]
    [true :routes.api/not-found]]])

(def ui-routes
  [""
   [["/" :routes.ui/home]]])

(def resource-routes
  [""
   [["/health" :routes.resources/health]
    ["/js" [[true :routes.resources/js]]]
    ["/css" [[true :routes.resources/css]]]]])

(def all
  [""
   [api-routes
    ui-routes
    resource-routes
    [true :routes/not-found]]])
