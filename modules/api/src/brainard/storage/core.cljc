(ns brainard.storage.core
  (:refer-clojure :exclude [read])
  (:require
    [brainard.storage.interfaces :as istorage]))

(defn query [this params]
  (istorage/read this (istorage/->input params)))

(def ^{:arglists '([node])} node->ref :brainard/ref)

(defn execute! [this & params]
  (istorage/write! this (mapcat istorage/->input params)))
