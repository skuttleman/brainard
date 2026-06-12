(ns brainard.infra.utils.routing
  "Tokenized HTTP routing table"
  (:require
   [reitit.coercion :as coercion]
   [reitit.coercion.malli :as rcm]
   [reitit.core :as r]))

(def ^:private api-routes
  [["/api" [["/attachments" {:name :routes.api/attachments}]
            ["/contexts" {:name :routes.api/contexts}]
            ["/notes" [["" {:name :routes.api/notes}]
                       ["/scheduled" {:name :routes.api/notes?scheduled}]
                       ["/:id" [["" {:name       :routes.api/note
                                     :param-keys {:id :notes/id}
                                     :parameters {:path [:map [:id :uuid]]}}]
                                ["/history" {:name       :routes.api/note?history
                                             :param-keys {:id :notes/id}
                                             :parameters {:path [:map [:id :uuid]]}}]
                                ["/reinstate" {:name       :routes.api/note!reinstate
                                               :param-keys {:id :notes/id}
                                               :parameters {:path [:map [:id :uuid]]}}]
                                ["/schedules" {:name       :routes.api/note#schedules
                                               :param-keys {:id :notes/id}
                                               :parameters {:path [:map [:id :uuid]]}}]]]]]
            ["/schedules" [["" {:name :routes.api/schedules}]
                           ["/:id" {:name       :routes.api/schedule
                                    :param-keys {:id :schedules/id}
                                    :parameters {:path [:map [:id :uuid]]}}]]]
            ["/tags" {:name :routes.api/tags}]
            ["/workspace-nodes" [["" {:name :routes.api/workspace-nodes}]
                                 ["/:id" {:name       :routes.api/workspace-node
                                          :param-keys {:id :workspace-nodes/id}
                                          :parameters {:path [:map [:id :uuid]]}}]]]
            ["/events" {:name :routes.api/events}]
            ["/*path" {:name :routes.api/not-found}]]]])

(def ^:private resource-routes
  [["/attachments/:id" {:name       :routes.resources/attachment
                        :param-keys {:id :attachments/id}
                        :parameters {:path [:map [:id :uuid]]}}]
   ["/exports/:id" {:name       :routes.resources/export
                    :param-keys {:id :notes/id}
                    :parameters {:path [:map [:id :uuid]]}}]
   ["/css/*path" {:name :routes.resources/css}]
   ["/img/*path" {:name :routes.resources/img}]
   ["/js/*path" {:name :routes.resources/js}]
   ["/favicon.ico" {:name :routes.resources/not-found}]])

(def ^:private ui-routes
  [["/" {:name :routes.ui/home}]
   ["/buzz" {:name :routes.ui/buzz}]
   ["/notes/:id" {:name       :routes.ui/note
                  :param-keys {:id :notes/id}
                  :parameters {:path [:map [:id :uuid]]}}]
   ["/search" {:name :routes.ui/search}]
   ["/*path" {:name :routes.ui/not-found}]])

(def all-routes
  (r/router (reduce into api-routes [resource-routes ui-routes])
            {:data      {:coercion rcm/coercion}
             :compile   coercion/compile-request-coercers
             :conflicts nil}))
