(ns brainard.api.utils.uuids
  "Utilities for generating uuids."
  (:require
    [#?(:clj clj-uuid :cljs com.yetanalytics.squuid) :as uuid]))

(def ^:const regex #"(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

(defn random []
  #?(:clj  (uuid/squuid)
     :cljs (uuid/generate-squuid)))

(defn ->uuid [x]
  #?(:clj  (uuid/as-uuid x)
     :cljs (cond-> x (string? x) uuid)))
