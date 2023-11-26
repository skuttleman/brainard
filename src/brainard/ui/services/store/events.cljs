(ns brainard.ui.services.store.events
  (:require
    [brainard.common.forms :as forms]))

(defn assoc-path [path]
  (fn [db [_ value]]
    (assoc-in db path value)))

(defn create-form [db [_ form-id data]]
  (assoc-in db [::forms/form form-id] (forms/create form-id data)))

(defn destroy-form [db [_ form-id]]
  (update db ::forms/form dissoc form-id))

(defn change-form [db [_ form-id path value]]
  (update-in db [::forms/form form-id]
             (fn [form]
               (-> form
                   (forms/change path value)
                   (forms/touch path)))))

(defn touch-form [db [_ form-id path :as action]]
  (if (= 2 (count action))
    (update-in db [::forms/form form-id] forms/touch)
    (update-in db [::forms/form form-id] forms/touch path)))

(defn submit-form [db [_ form-id validator]]
  (update-in db [::forms/form form-id] forms/attempt validator))

(defn form-invalid [db [_ form-id validator errors]]
  (update-in db [::forms/form form-id] forms/local-fail validator errors))

(defn form-failed [db [_ form-id errors]]
  (update-in db [::forms/form form-id] forms/fail-remote errors))
