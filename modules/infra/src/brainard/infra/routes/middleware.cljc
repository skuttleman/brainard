(ns brainard.infra.routes.middleware
  (:require
    [brainard.infra.routes.errors :as routes.err]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.api.utils.logger :as log]
    [brainard.infra.validations :as valid]
    [clojure.string :as string]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 399)))

(defn with-input
  "Includes route input as :brainard/input via [[iroutes/req->input]]"
  [handler]
  (fn [req]
    (handler (assoc req :brainard/input (iroutes/req->input req)))))

;; TODO - this is the wrong way
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

#?(:clj
   (defn ^:private log-req [{:keys [ex result duration]} {:keys [uri] :as req}]
     (let [status (:status result)
           duration (str "[" duration "ms]:")
           method (string/upper-case (name (:request-method req)))]
       (if (and (nil? ex) (success? status))
         (log/info method uri duration status)
         (log/error method uri duration status)))))

#?(:clj
   (defn with-logging
     "Logs request/response details."
     ([handler]
      (with-logging handler nil))
     ([handler {:keys [xform] :or {xform identity}}]
      (let [logger (xform log-req)]
        (fn [req]
          (log/with-duration [ctx (handler req)]
            (logger ctx req)))))))

#?(:clj
   (defn with-error-handling
     "Catches exceptions and generates an response via [[routes.err/ex->response]]."
     [handler]
     (fn [req]
       (try (handler req)
            (catch Throwable ex
              (log/error ex (ex-message ex) (ex-data ex))
              (routes.err/ex->response (ex-data ex)))))))
