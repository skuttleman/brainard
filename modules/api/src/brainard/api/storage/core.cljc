(ns brainard.api.storage.core
  (:refer-clojure :exclude [read])
  (:require
   [brainard.api.storage.interfaces :as istorage]))

(defn query
  "Run a storage query using the underlying istorage implementation."
  [this params]
  (istorage/read this (istorage/->input params)))

(defn execute!
  "Execute write operations on the given storage implementation."
  [this & params]
  (istorage/write! this (mapcat istorage/->input params))
  this)
