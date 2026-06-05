(ns brainard.api.notifications.core
  (:require
    [brainard.api.notifications.interfaces :as inotifications]))

(defn broadcast!
  "Broadcasts a message to all connections."
  [manager msg]
  (inotifications/broadcast! manager msg)
  nil)

(defn close!
  "Closes all connections."
  [manager]
  (inotifications/close! manager))
