(ns brainard.infra.utils.routing
  "Tokenized HTTP routing table"
  (:require
    [brainard.api.utils.uuids :as uuids]
    [reitit.core :as r]
    [whet.interfaces :as iwhet]))

(def ^:private token->coercers
  {:routes.api/note             {:notes/id uuids/->uuid}
   :routes.api/note?history     {:notes/id uuids/->uuid}
   :routes.api/note!reinstate   {:notes/id uuids/->uuid}
   :routes.api/note#schedules   {:notes/id uuids/->uuid}
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
  [["/api" [["/attachments" {:name :routes.api/attachments}]
            ["/contexts" {:name :routes.api/contexts}]
            ["/notes" [["" {:name :routes.api/notes}]
                       ["/scheduled" {:name :routes.api/notes?scheduled}]
                       ["/:id" [["" {:name       :routes.api/note
                                     :param-keys {:id :notes/id}}]
                                ["/history" {:name       :routes.api/note?history
                                             :param-keys {:id :notes/id}}]
                                ["/reinstate" {:name       :routes.api/note!reinstate
                                               :param-keys {:id :notes/id}}]
                                ["/schedules" {:name       :routes.api/note#schedules
                                               :param-keys {:id :notes/id}}]]]]]
            ["/schedules" [["" {:name :routes.api/schedules}]
                           ["/:id" {:name       :routes.api/schedule
                                    :param-keys {:id :schedules/id}}]]]
            ["/tags" {:name :routes.api/tags}]
            ["/workspace-nodes" [["" {:name :routes.api/workspace-nodes}]
                                 ["/:id" {:name       :routes.api/workspace-node
                                          :param-keys {:id :workspace-nodes/id}}]]]
            ["/events" {:name :routes.api/events}]
            ["/*path" {:name :routes.api/not-found}]]]])

(def ^:private resource-routes
  [["/attachments/:id"
    {:name       :routes.resources/attachment
     :param-keys {:id :attachments/id}}]
   ["/css/*path" {:name :routes.resources/css}]
   ["/img/*path" {:name :routes.resources/img}]
   ["/js/*path" {:name :routes.resources/js}]
   ["/favicon.ico" {:name :routes.resources/not-found}]])

(def ^:private ui-routes
  [["/" {:name :routes.ui/home}]
   ["/buzz" {:name :routes.ui/buzz}]
   ["/notes/:id" {:name       :routes.ui/note
                  :param-keys {:id :notes/id}}]
   ["/search" {:name :routes.ui/search}]
   ["/*path" {:name :routes.ui/not-found}]])

(def all-routes
  (r/router (vec (concat api-routes resource-routes ui-routes))
            {:conflicts nil}))

(comment
  (whet.utils.navigation/match all-routes (str "/api/notes/" (uuids/random) "?foo=bar&foo=rabbit")))
