(ns brainard.api.specs
  (:require
    [clojure.string :as string]))

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
