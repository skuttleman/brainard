(ns brainard.storage.core
  (:refer-clojure :exclude [read])
  (:require
    [brainard.storage.interfaces :as istorage]))

(defn read [this params]
  (istorage/read this params))

(defn write! [this params]
  (istorage/write! this params))

(defn query [this params]
  (read this (istorage/->input params)))

(defn execute! [this params]
  (write! this (istorage/->input params)))
