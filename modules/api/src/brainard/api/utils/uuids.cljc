(ns brainard.api.utils.uuids
  "Utilities for generating uuids."
  (:require
    [#?(:clj clj-uuid.core :cljs com.yetanalytics.squuid) :as uuid]))

(def ^:const regex #"(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

(defn random
  "Generate a time-ordered UUID (squuid)."
  []
  #?(:clj  (uuid/squuid)
     :cljs (uuid/generate-squuid)))

(defn ->uuid
  "Convert x to a UUID."
  [x]
  #?(:clj  (cond-> x (string? x) parse-uuid)
     :cljs (cond-> x (string? x) uuid)))
