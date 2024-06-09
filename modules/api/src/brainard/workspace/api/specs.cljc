(ns brainard.workspace.api.specs
  (:require
    [clojure.string :as string]
    [workspace-nodes :as-alias ws]))

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
   [::ws/content non-empty-trimmed-string]
   [::ws/parent-id {:optional true} uuid?]])

(def full
  [:schema {:registry {::full (conj create
                                    [::ws/id uuid?]
                                    [::ws/children {:optional true}
                                     [:sequential [:ref ::full]]])}}
   [:ref ::full]])

(def modify
  [:map
   [::ws/content {:optional true} non-empty-trimmed-string]
   [::ws/parent-id {:optional true} [:maybe uuid?]]
   [::ws/prev-sibling-id {:optional true} uuid?]])
