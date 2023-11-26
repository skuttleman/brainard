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

(defn add-tags [{[status] :tags :as db} [_ {:notes/keys [tags]}]]
  (cond-> db
    (= :success status)
    (update-in [:tags 1] into tags)))

(defn add-context [{[status] :tags :as db} [_ {:notes/keys [context]}]]
  (cond-> db
    (and context (= :success status))
    (update-in [:contexts 1] conj context)))

(defn show-toast [db [_ toast-id]]
  (cond-> db
    (get-in db [:toasts toast-id])
    (assoc-in [:toasts toast-id :state] :visible)))

(defn destroy-toast [db [_ toast-id]]
  (update db :toasts dissoc toast-id))
