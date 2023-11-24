(ns brainard.common.validations
  (:require
    [malli.core :as m]
    [malli.error :as me]))

(defn validate! [spec data type]
  (when-let [errors (some-> spec (m/explain data))]
    (throw (ex-info "failed spec validation"
                    {::type   type
                     :data    data
                     :details (me/humanize errors)}))))
