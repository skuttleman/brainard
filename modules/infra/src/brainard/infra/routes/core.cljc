(ns brainard.infra.routes.core
  (:require
    #?@(:clj [[brainard.infra.utils.routing :as rte]
              [ring.middleware.keyword-params :as ring.kw-params]
              [ring.middleware.params :as ring.params]])
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.middleware :as mw]
    [brainard.infra.routes.response :as routes.res]
    [whet.core :as w]))

#?(:clj
   (defn ^:private asset? [req]
     (re-matches #"^/(js|css|img|favicon).*$" (:uri req))))

(defmethod iroutes/req->input :default
  [{::w/keys [route] :as req}]
  (or (:body req)
      (merge (:route-params route)
             (:query-params route))))

(defmethod iroutes/handler :default
  [_]
  (routes.res/->response 404 "Not found" {"content-type" "plain/text"}))

(defmethod iroutes/handler [:any :routes.api/not-found]
  [_]
  (routes.res/->response 404 (routes.res/errors "Not found" :UNKNOWN_API)))

(def handler
  "Main app handler"
  (-> iroutes/handler
      mw/with-spec-validation
      mw/with-input
      #?(:clj (w/with-middleware rte/all-routes))))

#?(:clj
   (def be-handler
     "Handles all HTTP requests through the webserver."
     (-> #'handler
         ring.kw-params/wrap-keyword-params
         ring.params/wrap-params
         mw/with-error-handling
         (mw/with-logging {:xform (remove asset?)}))))
