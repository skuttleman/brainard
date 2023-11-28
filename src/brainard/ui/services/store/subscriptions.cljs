(ns brainard.ui.services.store.subscriptions
  (:require
    [brainard.common.forms :as forms]))

(defn get-path [path]
  (fn [db _]
    (get-in db path)))

(defn form [db [_ form-id]]
  (get-in db [::forms/form form-id]))

(defn toasts [db _]
  (->> (:toasts db)
       (sort-by key)
       (take 3)))

(defn resource [db [_ handle]]
  (get-in db [:resources/resources handle] [:init]))
