(ns brainard.infra.routes.core
  (:require
   #?@(:clj [[brainard.attachments.infra.multipart-params :as multi]
             [brainard.infra.utils.routing :as rte]
             [ring.middleware.keyword-params :as ring.kw-params]
             [ring.middleware.params :as ring.params]])
   [brainard :as-alias b]
   [brainard.api.core :as api]
   [brainard.api.events.core :as events]
   [brainard.infra.routes.interfaces :as iroutes]
   [brainard.infra.routes.middleware :as mw]
   [brainard.infra.routes.response :as routes.res]
   [clojure.core.async :as async]
   [slag.utils.uuids :as uuids]
   [whet.core :as w]))

(def ^:private ^:const not-found-resource
  (routes.res/->response 404 (routes.res/errors :UNKNOWN_RESOURCE "Not found")))

(defmethod iroutes/req->input :default
  [{::w/keys [route] :as req}]
  (merge {:request-id (uuids/->uuid (get-in req [:headers "x-request-id"]))}
         (:body req)
         (:route-params route)
         (:query-params route)))

(defmethod iroutes/handler :default
  [_ respond _raise]
  (respond (routes.res/->response 404 "Not found" {"content-type" "plain/text"})))

(defmethod iroutes/handler [:read :routes/api]
  [{::b/keys [apis input] :as req} respond _raise]
  (let [response (when-let [handle (iroutes/route->handler (iroutes/router req))]
                   (when-let [result (api/invoke-api handle apis input)]
                     (routes.res/->response 200 {:data result})))]
    (respond (or response not-found-resource))))

(defmethod iroutes/handler [:post :routes.api/attachments]
  [{::b/keys [apis input] :as req} respond _raise]
  (let [response (when-let [handle (iroutes/route->handler (iroutes/router req))]
                   (when-let [result (api/invoke-api handle apis input)]
                     (routes.res/->response 201 {:data result})))]
    (respond (or response not-found-resource))))

(defmethod iroutes/handler [:write :routes/api]
  [{::b/keys [apis input] :as req} respond _raise]
  (let [response (when-let [handle (iroutes/route->handler (iroutes/router req))]
                   (async/go
                     (let [{:keys [request-id]} input]
                       (try (let [[event result] (api/invoke-api handle apis input)]
                              (when event
                                (events/broadcast! (:events apis)
                                                   event
                                                   {:request-id request-id
                                                    :data       result})))
                            (catch #?(:cljs :default :default Throwable) ex
                              (let [data (ex-data ex)
                                    msg (ex-message ex)]
                                (events/broadcast! (:events apis)
                                                   :api/error
                                                   {:errors [(assoc data :message msg)]}))))))
                   (routes.res/->response 204))]
    (respond (or response not-found-resource))))

(defmethod iroutes/handler [:any :routes.api/not-found]
  [_ respond _raise]
  (respond (routes.res/->response 404 (routes.res/errors :UNKNOWN_API "Not found"))))

(def handler
  "Main app handler"
  (-> iroutes/handler
      mw/with-input
      #?@(:clj [multi/wrap-multipart-params
                (w/with-middleware rte/all-routes)])))

#?(:clj
   (def be-handler
     "Handles all HTTP requests through the webserver."
     (-> #'handler
         ring.kw-params/wrap-keyword-params
         ring.params/wrap-params
         mw/with-error-handling
         (mw/with-logging {:xform (filter #(re-matches
                                            #"^/((api|attachments|exports)/.*)?$"
                                            (:uri %)))}))))
