(ns brainard.infra.routes.middleware
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.specs :as specs]
    [brainard.common.utils.edn :as edn]
    [brainard.infra.routes.common :as routes.common]
    [brainard.infra.routes.errors :as err]
    [brainard.infra.routes.table :as table]
    [ring.util.request :as ring.req]
    [taoensso.timbre :as log]))

(defn with-error-handling [handler]
  (fn [req]
    (try (handler req)
         (catch Throwable ex
           (log/error ex (select-keys req #{:uri :request-method}))
           (err/ex->response (ex-data ex))))))

(defn with-routing [handler]
  (fn [req]
    (let [route-info (bidi/match-route table/all (:uri req))]
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
      (err/validate! input-spec (:brainard/input req) ::err/input-validation)
      (let [result (handler req)
            output-spec (specs/output-specs spec-key)]
        (err/validate! output-spec (:body result) ::err/output-validation)
        result))))
