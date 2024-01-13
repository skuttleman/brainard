(ns brainard.common.routes.middleware
  (:require
    [brainard.common.routes.interfaces :as iroutes]
    [brainard.api.utils.logger :as log]
    [brainard.api.utils.routing :as rte]
    [brainard.common.store.validations :as valid]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 399)))

(defn with-routing
  "Includes routing data on the request."
  [handler]
  (fn [req]
    (let [route-info (rte/match (cond-> (:uri req)
                                  (:query-string req) (str "?" (:query-string req))))]
      (handler (assoc req :brainard/route route-info)))))

(defn with-input
  "Includes route input as :brainard/input via [[iroutes/req->input]]"
  [handler]
  (fn [req]
    (handler (assoc req :brainard/input (iroutes/req->input req)))))

(defn with-spec-validation
  "Handles input/output spec validation for spec'd routes."
  [handler]
  (fn [req]
    (let [spec-key (iroutes/router req)
          input-spec (valid/input-specs spec-key)]
      (some-> input-spec (valid/validate! (:brainard/input req) ::valid/input-validation))
      (let [{:keys [body] :as response} (handler req)]
        (when-let [output-spec (valid/output-specs spec-key)]
          (let [validator (valid/->validator output-spec)
                err-validator (valid/->validator valid/api-errors)]
            (when-let [errors (and (err-validator body)
                                   (validator body))]
              (log/warn "returning invalid response to client:" (pr-str body))
              (log/warn (pr-str errors)))))
        response))))
