(ns brainard.infra.routes.middleware
  (:require
    [brainard.common.navigation.core :as nav]
    [brainard.common.specs :as specs]
    [brainard.common.utils.edn :as edn]
    [brainard.common.utils.logger :as log]
    [brainard.common.validations :as valid]
    [brainard.infra.routes.errors :as err]
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



(defn with-routing [handler]
  (fn [req]
    (let [route-info (nav/match (:uri req))]
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

(defn with-input [handler]
  (fn [req]
    (handler (assoc req :brainard/input (iroutes/req->input req)))))

(defn with-spec-validation [handler]
  (fn [req]
    (let [spec-key (iroutes/router req)
          input-spec (specs/input-specs spec-key)]
      (some-> input-spec (valid/validate! (:brainard/input req) ::valid/input-validation))
      (let [response (handler req)
            output-spec (if (success? (:status response))
                          (specs/output-specs spec-key)
                          specs/errors)]
        ;; TODO - dev only
        (some-> output-spec (valid/validate! (:body response) ::valid/output-validation))
        response))))
