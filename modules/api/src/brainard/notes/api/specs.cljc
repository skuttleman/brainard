(ns brainard.notes.api.specs
  (:require
    [malli.util :as mu]))

(def create
  [:map
   [:notes/context string?]
   [:notes/body string?]
   [:notes/tags {:optional true} [:set keyword?]]])

(def full
  (mu/merge create
            [:map
             [:notes/id uuid?]
             [:notes/timestamp inst?]]))

(def modify
  [:map
   [:notes/context {:optional true} string?]
   [:notes/body {:optional true} string?]
   [:notes/tags {:optional true} [:set keyword?]]
   [:notes/tags!remove {:optional true} [:set keyword?]]])

(def query
  [:and
   [:map
    [:notes/context {:optional true} string?]
    [:notes/tags {:optional true} [:set keyword?]]]
   [:fn {:error/message "must select at least one tag or topic"}
    (some-fn (comp seq :notes/tags)
             (comp some? :notes/context))]])
