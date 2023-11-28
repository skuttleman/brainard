(ns brainard.infra.routes.middleware
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.routing :as routing]
    [brainard.common.specs :as specs]
    [brainard.common.utils.edn :as edn]
    [brainard.common.utils.logger :as log]
    [brainard.common.validations :as valid]
    [brainard.infra.routes.common :as routes.common]
    [brainard.infra.routes.errors :as err]
    [clojure.string :as string]
    [ring.util.request :as ring.req]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 399)))

(defn ^:private log-req [{:keys [ex result duration]} {:keys [uri] :as req}]
  (let [status (:status result)
        duration (str "[" duration "ms]:")
        method (string/upper-case (name (:request-method req)))]
    (if (and (nil? ex) (success? status))
      (log/info method uri duration status)
      (log/error method uri duration status))))

(defn with-logging
  ([handler]
   (with-logging handler nil))
  ([handler {:keys [xform] :or {xform identity}}]
   (let [logger (xform log-req)]
     (fn [req]
       (log/with-duration [ctx (handler req)]
         (logger ctx req))))))

(defn with-error-handling [handler]
  (fn [req]
    (try (handler req)
         (catch Throwable ex
           (log/error ex (ex-message ex) (ex-data ex))
           (err/ex->response (ex-data ex))))))

(defn ^:private coerce-params [params handler]
  (reduce (fn [params [k coercer]]
            (cond-> params
              (contains? params k)
              (update k coercer)))
          params
          (meta handler)))

(defn with-routing [handler]
  (fn [req]
    (let [route-info (bidi/match-route routing/all (:uri req))
          route-info (-> route-info
                         (update :route-params coerce-params (:handler route-info))
                         (update :handler keyword))]
      (handler (assoc req :brainard/route route-info)))))

(defn with-edn [handler]
  (fn [req]
    (let [content-type (or (ring.req/content-type req)
                           "application/edn")
          response (-> req
                       (cond-> (= content-type "application/edn")
                               (update :body edn/read))
                       handler)]
      (cond-> response
        (and (nil? (get-in response [:headers "content-type"]))
             (some? (:body response)))
        (-> (assoc-in [:headers "content-type"] "application/edn")
            (update :body pr-str))))))

(defn with-spec-validation [handler]
  (fn [req]
    (let [req (routes.common/coerce-input req)
          spec-key (routes.common/router req)
          input-spec (specs/input-specs spec-key)]
      (valid/validate! input-spec (:brainard/input req) ::valid/input-validation)
      (let [response (handler req)
            output-spec (if (success? (:status response))
                          (specs/output-specs spec-key)
                          specs/errors)]
        (valid/validate! output-spec (:body response) ::valid/output-validation)
        response))))
