(ns brainard.common.validations
  (:require
    [malli.core :as m]
    [malli.error :as me]))

(defn ->validator [spec]
  (fn [data]
    (when-let [errors (m/explain spec data)]
      (me/humanize errors))))

(defn validate! [spec data type]
  (let [validator (->validator spec)]
    (when-let [details (validator data)]
      (throw (ex-info "failed spec validation"
                      {::type   type
                       :data    data
                       :details details})))))
