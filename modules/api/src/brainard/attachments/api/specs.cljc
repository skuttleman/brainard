(ns brainard.attachments.api.specs
  (:require
    [brainard.api.specs :as scommon]
    [malli.util :as mu]))

(def modify
  [:map
   [:attachments/id uuid?]
   [:attachments/name scommon/non-empty-trimmed-string]])

(def full
  (mu/merge modify
            [:map
             [:attachments/filename scommon/non-empty-trimmed-string]
             [:attachments/content-type scommon/non-empty-trimmed-string]]))
