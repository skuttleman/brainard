(ns brainard.common.routing)

(def ^:private uuid-regex #"(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

(def api-routes
  ["/api"
   [["/notes"
     [["" :routes.api/notes]
      [["/" [uuid-regex :notes/id]] :routes.api/note]]]
    ["/tags"
     [["" :routes.api/tags]]]
    ["/contexts"
     [["" :routes.api/contexts]]]
    [true :routes.api/not-found]]])

(def resource-routes
  [""
   [["/js" [[true :routes.resources/js]]]
    ["/css" [[true :routes.resources/css]]]]])

(def ui-routes
  [""
   [["/" :routes/ui]]])

(def all
  [""
   [api-routes
    resource-routes
    ui-routes
    [true :routes/not-found]]])
