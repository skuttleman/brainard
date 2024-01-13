(ns brainard.infra.routes.base
  (:require
    [brainard.infra.routes.errors :as routes.err]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.middleware :as mwc]
    [brainard.infra.routes.response :as routes.res]
    [brainard.infra.utils.edn :as edn]
    [clojure.set :as set]))

(defn ^:private cljs-http->ring [handler]
  (fn [req]
    (try
      (let [response (-> req
                         (set/rename-keys {:url :uri})
                         (cond-> (string? (:body req)) (update :body edn/read-string))
                         handler)]
        (cond-> response
          (string? (:body response)) (update :body edn/read-string)))
      (catch #?(:cljs :default :default Throwable) ex
        (routes.err/ex->response (ex-data ex))))))

(defmethod iroutes/req->input :default
  [{:brainard/keys [route] :as req}]
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
      mwc/with-spec-validation
      mwc/with-input
      mwc/with-routing))

(def ui-handler
  "Main ui app handler"
  (cljs-http->ring handler))
