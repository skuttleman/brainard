(ns brainard.workspace.api.specs
  (:require
    [brainard.api.specs :as scommon]
    [workspace-nodes :as-alias ws]))

(def create
  [:map
   [::ws/content scommon/non-empty-trimmed-string]
   [::ws/parent-id {:optional true} uuid?]])

(def full
  [:schema {:registry {::full (conj create
                                    [::ws/id uuid?]
                                    [::ws/children {:optional true}
                                     [:sequential [:ref ::full]]])}}
   [:ref ::full]])

(def modify
  [:map
   [::ws/content {:optional true} scommon/non-empty-trimmed-string]
   [::ws/parent-id {:optional true} [:maybe uuid?]]
   [::ws/prev-sibling-id {:optional true} uuid?]])
