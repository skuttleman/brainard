(ns brainard.api.events.core
  (:require
    [brainard.api.events.interfaces :as ievents]))

(defn broadcast!
  "Broadcasts a message to all connections."
  [manager type data]
  (ievents/broadcast! manager type data)
  nil)

(defn close!
  "Closes all connections."
  [manager]
  (ievents/close! manager))
