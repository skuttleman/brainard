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
  (update-in db [::forms/form form-id] forms/change path value))

(defn add-tags [{[status] :api.tags/fetch :as db} [_ {:notes/keys [tags]}]]
  (cond-> db
    (= :success status)
    (update-in [:api.tags/fetch 1] into tags)))

(defn add-context [{[status] :api.contexts/fetch :as db} [_ {:notes/keys [context]}]]
  (cond-> db
    (and context (= :success status))
    (update-in [:api.contexts/fetch 1] conj context)))

(defn submit-resource [db [_ resource-id]]
  (assoc-in db [:resources/resources resource-id] [:requesting]))

(defn resource-succeeded [db [_ resource-id data]]
  (let [existing (get-in db [:resources/resources resource-id])]
    (cond-> db
      existing (assoc-in [:resources/resources resource-id] [:success data]))))

(defn resource-failed [db [_ resource-id errors]]
  (let [existing (get-in db [:resources/resources resource-id])]
    (cond-> db
      existing (assoc-in [:resources/resources resource-id] [:error errors]))))

(defn destroy-resource [db [_ resource-id]]
  (update db :resources/resources dissoc resource-id))
