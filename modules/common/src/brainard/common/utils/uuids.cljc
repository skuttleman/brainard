(ns brainard.common.utils.uuids
  "Utilities for generating uuids."
  #?(:clj
     (:require
       [clj-uuid :as uuid])))

(def ^:const regex #"(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

(defn random []
  #?(:clj     (uuid/squuid)
     :default (random-uuid)))

(defn ->uuid [x]
  #?(:clj  (uuid/as-uuid x)
     :cljs (cond-> x (string? x) uuid)))
