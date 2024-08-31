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

(def contact
  [:map
   [:contacts/name non-empty-trimmed-string]
   [:contacts/email
    {:optional true}
    [:and
     [:string]
     [:fn {:error/message "invalid email"}
      (partial re-matches #"([a-z0-9\.-_])+@([a-z0-9-_])+\.([a-z0-9\.-_])+")]]]
   [:contacts/phone
    {:optional true}
    [:and
     [:string]
     [:fn {:error/message "invalid phone"}
      (partial re-matches #"\d{10}")]]]])

(def create
  [:map
   [:applications/company
    [:map
     [:companies/name non-empty-trimmed-string]
     [:companies/website {:optional true} string?]
     [:companies/location {:optional true} string?]
     [:companies/contacts {:optional true} [:sequential contact]]]]
   [:applications/job-title {:optional true} string?]
   [:applications/details {:optional true} string?]])

(def modify
  [:map
   [:applications/company
    {:optional true}
    [:map
     [:companies/name {:optional true} non-empty-trimmed-string]
     [:companies/website {:optional true} string?]
     [:companies/location {:optional true} string?]]]
   [:applications/job-title {:optional true} string?]
   [:applications/details {:optional true} string?]])
