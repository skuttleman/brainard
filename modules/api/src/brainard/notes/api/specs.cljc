(ns brainard.notes.api.specs
  (:require
    [clojure.string :as string]
    [malli.util :as mu]))

(def non-empty-string
  [:and
   [:string]
   [:fn {:error/message "must not be a blank string"}
    (complement string/blank?)]])

(def non-empty-trimmed-string
  [:and
   non-empty-string
   [:fn {:error/message "must not have leading or trailing whitespace"}
    (fn [s]
      (= s (string/trim s)))]])

(def create
  [:map
   [:notes/context non-empty-trimmed-string]
   [:notes/body non-empty-string]
   [:notes/pinned? boolean?]
   [:notes/tags {:optional true} [:set keyword?]]])

(def full
  (mu/merge create
            [:map
             [:notes/id uuid?]
             [:notes/timestamp inst?]]))

(def modify
  [:map
   [:notes/context {:optional true} non-empty-trimmed-string]
   [:notes/body {:optional true} non-empty-string]
   [:notes/tags {:optional true} [:set keyword?]]
   [:notes/tags!remove {:optional true} [:set keyword?]]
   [:notes/pinned? {:optional true} boolean?]])

(def query
  [:and
   [:map
    [:notes/ids {:optional true} [:set uuid?]]
    [:notes/context {:optional true} non-empty-trimmed-string]
    [:notes/tags {:optional true} [:set keyword?]]
    [:notes/pinned? {:optional true} true?]]
   [:fn {:error/message "must select at least one: ids, tag, topic, or pinned"}
    (some-fn (comp seq :notes/ids)
             (comp true? :notes/pinned?)
             (comp some? :notes/context)
             (comp seq :notes/tags))]])
