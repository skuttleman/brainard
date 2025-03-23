(ns brainard.infra.routes.errors
  (:require
    [brainard.api.validations :as valid]
    [brainard.infra.routes.response :as routes.res]))

(defn ^:private ->err-response [status body]
  (routes.res/->response status (pr-str body) {"content-type" "application/edn"}))

(defmulti ^{:arglists '([ex-data])} ex->response
          "Produces an HTTP response from exception details thrown by [[valid/validate!]]."
          ::valid/type)

(defmethod ex->response :default
  [_]
  (->err-response 500
                  (routes.res/errors :UNKNOWN "An unknown error occurred")))

(defmethod ex->response ::valid/input-validation
  [data]
  (->err-response 400
                  (routes.res/errors :INVALID_INPUT
                                     "Invalid input"
                                     {:details (:details data)})))

(defmethod ex->response ::valid/output-validation
  [data]
  (->err-response 500
                  (routes.res/errors :INVALID_OUTPUT
                                     "Unable to generate valid output"
                                     {:details (:details data)})))

(defmethod ex->response ::valid/upload-too-big
  [data]
  (->err-response 400
                  (routes.res/errors :FILE_UPLOAD_EXCEEDS_MAX_ALLOWED
                                     "A file being uploaded exceeds the maximum allowed size"
                                     {:details (:details data)})))
