(ns brainard.ui.services.store.events
  (:require
    [brainard.common.forms :as forms]))

(defn assoc-path [path]
  (fn [db [_ value]]
    (assoc-in db path value)))

(defn create-form [db [_ form-id data opts]]
  (assoc-in db [:forms/forms form-id] (forms/create form-id data opts)))

(defn destroy-form [db [_ form-id]]
  (update db :forms/forms dissoc form-id))

(defn change-form [db [_ form-id path value]]
  (update-in db [:forms/forms form-id] forms/change path value))

(defn add-tags [{:resources/keys [resources] :as db} [_ {:notes/keys [tags]}]]
  (let [[status] (:api.tags/fetch resources)]
    (cond-> db
      (= :success status)
      (update-in [:resources/resources :api.tags/fetch 1] into tags))))

(defn add-context [{:resources/keys [resources] :as db} [_ {:notes/keys [context]}]]
  (let [[status] (:api.contexts/fetch resources)]
    (cond-> db
      (and context (= :success status))
      (update-in [:resources/resources :api.contexts/fetch 1] conj context))))

(defn resource-succeeded [db [_ resource-id data]]
  (assoc-in db [:resources/resources resource-id] [:success data]))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) (partial merge-with conj) nil warnings))

(defn resource-failed [db [_ resource-id source errors]]
  (let [errors (cond-> errors (= :remote source) remote->warnings)]
    (assoc-in db [:resources/resources resource-id] [:error {source errors}])))

(defn destroy-resource [db [_ resource-id]]
  (update db :resources/resources dissoc resource-id))
