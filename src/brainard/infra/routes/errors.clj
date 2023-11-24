(ns brainard.infra.routes.errors
  (:require [malli.core :as m]
            [malli.error :as me]))

(defn ^:private ->err-response [status body]
  {:status  status
   :headers {"content-type" "application/edn"}
   :body    (pr-str body)})

(defmulti ex->response ::type)

(defmethod ex->response :default
  [_]
  (->err-response 500
                  {:errors [{:message "An unknown error occurred"
                             :code    :UNKNOWN}]}))

(defmethod ex->response ::input-validation
  [data]
  (->err-response 400
                  {:errors [{:message "Invalid input"
                             :code    :INVALID_INPUT
                             :details (:details data)}]}))

(defmethod ex->response ::output-validation
  [data]
  (->err-response 500
                  {:errors [{:message "Unable to generate valid output"
                             :code    :INVALID_OUTPUT
                             :details (:details data)}]}))

(defn validate! [spec data type]
  (when-let [errors (some-> spec (m/explain data))]
    (throw (ex-info "failed spec validation"
                    {::type type
                     :data      data
                     :details   (me/humanize errors)}))))
