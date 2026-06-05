(ns brainard.api.notifications.core
  (:require
    [brainard.api.notifications.interfaces :as inotifications]))

(defn broadcast!
  ""
  [manager msg]
  (inotifications/broadcast! manager msg)
  nil)

(defn close!
  ""
  [manager]
  (inotifications/close! manager))
