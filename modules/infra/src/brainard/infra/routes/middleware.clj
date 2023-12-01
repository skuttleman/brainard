(ns brainard.infra.routes.middleware
  (:require
    [brainard.common.services.navigation.core :as nav]
    [brainard.common.services.validations.core :as valid]
    [brainard.common.utils.edn :as edn]
    [brainard.common.utils.logger :as log]
    [brainard.infra.routes.errors :as routes.err]
    [brainard.infra.routes.interfaces :as iroutes]
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
  "Logs request/response details."
  ([handler]
   (with-logging handler nil))
  ([handler {:keys [xform] :or {xform identity}}]
   (let [logger (xform log-req)]
     (fn [req]
       (log/with-duration [ctx (handler req)]
         (logger ctx req))))))

(defn with-error-handling
  "Catches exceptions and generates an response via [[routes.err/ex->response]]."
  [handler]
  (fn [req]
    (try (handler req)
         (catch Throwable ex
           (log/error ex (ex-message ex) (ex-data ex))
           (routes.err/ex->response (ex-data ex))))))

(defn with-routing
  "Includes routing data on the request."
  [handler]
  (fn [req]
    (let [route-info (nav/match (:uri req))]
      (handler (assoc req :brainard/route route-info)))))

(defn with-edn
  "Serializes/deserializes request/response data as `edn`."
  [handler]
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

(defn with-input
  "Includes route input as :brainard/input via [[iroutes/req->input]]"
  [handler {:keys [req->input]}]
  (fn [req]
    (handler (assoc req :brainard/input (req->input req)))))

(defn with-spec-validation
  "Handles input/output spec validation for spec'd routes."
  [handler]
  (fn [req]
    (let [spec-key (iroutes/router req)
          input-spec (valid/input-specs spec-key)]
      (some-> input-spec (valid/validate! (:brainard/input req) ::valid/input-validation))
      (let [response (handler req)
            output-spec (if (success? (:status response))
                          (valid/output-specs spec-key)
                          valid/api-errors)]
        ;; TODO - dev only
        (some-> output-spec (valid/validate! (:body response) ::valid/output-validation))
        response))))
