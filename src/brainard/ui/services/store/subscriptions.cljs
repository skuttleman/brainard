(ns brainard.ui.services.store.subscriptions
  (:require
    [brainard.common.forms :as forms]))

(defn get-path [path]
  (fn [db _]
    (get-in db path)))

(defn form [db [_ form-id]]
  (get-in db [::forms/form form-id]))
