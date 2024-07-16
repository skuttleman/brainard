(ns brainard.applications.api.specs
  (:require
    [clojure.string :as string]))

(def non-empty-trimmed-string
  [:and
   [:string]
   [:fn {:error/message "must not be a blank string"}
    (complement string/blank?)]
   [:fn {:error/message "must not have leading or trailing whitespace"}
    (fn [s]
      (= s (string/trim s)))]])

(def create
  [:map
   [:applications/company
    [:map
     [:companies/name non-empty-trimmed-string]
     [:companies/website {:optional true} string?]
     [:companies/location {:optional true} string?]]]
   [:applications/job-title {:optional true} string?]
   [:applications/details {:optional true} string?]])
