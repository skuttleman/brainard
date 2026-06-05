(ns brainard.api.events.core
  (:require
    [brainard.api.events.interfaces :as ievents]))

(defn broadcast!
  "Broadcasts a message to all connections."
  [manager msg]
  (ievents/broadcast! manager msg)
  nil)

(defn close!
  "Closes all connections."
  [manager]
  (ievents/close! manager))
