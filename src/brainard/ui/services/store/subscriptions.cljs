(ns brainard.ui.services.store.subscriptions
  (:require
    [brainard.common.forms :as forms]))

(defn get-path [path]
  (fn [db _]
    (get-in db path)))

(defn form-value [db [_ form-id]]
  (forms/current (get-in db [::forms/form form-id])))

(defn form-changed? [db [_ form-id path :as action]]
  (let [form (get-in db [::forms/form form-id])]
    (if (= 2 (count action))
      (forms/changed? form)
      (forms/changed? form path))))

(defn form-touched? [db [_ form-id path :as action]]
  (let [form (get-in db [::forms/form form-id])]
    (if (= 2 (count action))
      (forms/touched? form)
      (forms/touched? form path))))

(defn form-status [db [_ form-id]]
  (let [form (get-in db [::forms/form form-id])]
    (forms/status form)))

(defn form-errors [db [_ form-id]]
  (let [form (get-in db [::forms/form form-id])]
    (forms/errors form)))

(defn form-warnings [db [_ form-id]]
  (let [form (get-in db [::forms/form form-id])]
    (forms/warnings form)))
