(ns brainard.ui.store.subscriptions
  (:require
    [brainard.common.forms :as forms]))

(defn get-path [path]
  (fn [db _]
    (get-in db path)))

(defn form-value [db [_ id]]
  (forms/current (get-in db [::forms/form id])))

(defn form-errors [db [_ id]]
  (forms/errors (get-in db [::forms/form id])))

(defn form-changed? [db [_ id path :as action]]
  (let [form (get-in db [::forms/form id])]
    (if (= 2 (count action))
      (forms/changed? form)
      (forms/changed? form path))))

(defn form-touched? [db [_ id path :as action]]
  (let [form (get-in db [::forms/form id])]
    (if (= 2 (count action))
      (forms/touched? form)
      (forms/touched? form path))))
