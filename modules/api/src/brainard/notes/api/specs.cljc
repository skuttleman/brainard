(ns brainard.notes.api.specs
  (:require
    [malli.util :as mu]))

(def create
  [:map
   [:notes/context string?]
   [:notes/body string?]
   [:notes/pinned? {:optional true} boolean?]
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
   [:notes/tags!remove {:optional true} [:set keyword?]]
   [:notes/pinned? {:optional true} boolean?]])

(def query
  [:and
   [:map
    [:notes/ids {:optional true} [:set uuid?]]
    [:notes/context {:optional true} string?]
    [:notes/tags {:optional true} [:set keyword?]]
    [:notes/pinned? {:optional true} true?]]
   [:fn {:error/message "must select at least one: ids, tag, topic, or pinned"}
    (some-fn (comp seq :notes/ids)
             (comp true? :notes/pinned?)
             (comp some? :notes/context)
             (comp seq :notes/tags))]])
