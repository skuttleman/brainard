(ns brainard.applications.api.specs
  (:require
    [brainard.api.specs :as scommon]))

(def contact scommon/contact)

(def create
  [:map
   [:applications/company
    [:map
     [:companies/name scommon/non-empty-trimmed-string]
     [:companies/website {:optional true} string?]
     [:companies/location {:optional true} string?]
     [:companies/contacts {:optional true} [:sequential scommon/contact]]]]
   [:applications/job-title {:optional true} string?]
   [:applications/details {:optional true} string?]])

(def modify
  [:map
   [:applications/company
    {:optional true}
    [:map
     [:companies/name {:optional true} scommon/non-empty-trimmed-string]
     [:companies/website {:optional true} string?]
     [:companies/location {:optional true} string?]]]
   [:applications/job-title {:optional true} string?]
   [:applications/details {:optional true} string?]])
