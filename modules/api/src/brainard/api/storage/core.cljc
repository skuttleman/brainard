(ns brainard.api.storage.core
  (:refer-clojure :exclude [read])
  (:require
    [brainard.api.storage.interfaces :as istorage]))

(defn query [this params]
  (istorage/read this (istorage/->input params)))

(defn execute! [this & params]
  (istorage/write! this (mapcat istorage/->input params)))
