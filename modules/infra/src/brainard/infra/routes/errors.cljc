(ns brainard.infra.routes.errors
  (:require
    [brainard.infra.routes.response :as routes.res]
    [brainard.infra.validations :as valid]))

(defn ^:private ->err-response [status body]
  (routes.res/->response status (pr-str body) {"content-type" "application/edn"}))

(defmulti ^{:arglists '([ex-data])} ex->response
          "Produces an HTTP response from exception details thrown by [[valid/validate!]]."
          ::valid/type)

(defmethod ex->response :default
  [_]
  (->err-response 500
                  (routes.res/errors "An unknown error occurred"
                                     :UNKNOWN)))

(defmethod ex->response ::valid/input-validation
  [data]
  (->err-response 400
                  (routes.res/errors "Invalid input"
                                     :INVALID_INPUT
                                     {:details (:details data)})))

(defmethod ex->response ::valid/output-validation
  [data]
  (->err-response 500
                  (routes.res/errors "Unable to generate valid output"
                                     :INVALID_OUTPUT
                                     {:details (:details data)})))
