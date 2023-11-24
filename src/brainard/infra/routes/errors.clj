(ns brainard.infra.routes.errors
  (:require
    [brainard.common.validations :as valid]))

(defn ^:private ->err-response [status body]
  {:status  status
   :headers {"content-type" "application/edn"}
   :body    (pr-str body)})

(defmulti ex->response ::valid/type)

(defmethod ex->response :default
  [_]
  (->err-response 500
                  {:errors [{:message "An unknown error occurred"
                             :code    :UNKNOWN}]}))

(defmethod ex->response ::valid/input-validation
  [data]
  (->err-response 400
                  {:errors [{:message "Invalid input"
                             :code    :INVALID_INPUT
                             :details (:details data)}]}))

(defmethod ex->response ::valid/output-validation
  [data]
  (->err-response 500
                  {:errors [{:message "Unable to generate valid output"
                             :code    :INVALID_OUTPUT
                             :details (:details data)}]}))
