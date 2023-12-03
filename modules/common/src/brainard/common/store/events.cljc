(ns brainard.common.store.events
  (:require
    [brainard.common.forms.core :as forms]
    [yast.core :as yast]))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) (partial merge-with conj) nil warnings))

(defmethod yast/event-handler :routing/navigated
  [db [_ routing-info]]
  (assoc db :routing/info routing-info))

(defmethod yast/event-handler :resources/submitted
  [db [_ resource-id]]
  (assoc-in db [:resources/resources resource-id] [:requesting]))

(defmethod yast/event-handler :resources/succeeded
  [db [_ resource-id data]]
  (assoc-in db [:resources/resources resource-id] [:success data]))

(defmethod yast/event-handler :resources/failed
  [db [_ resource-id source errors]]
  (let [errors (cond-> errors (= :remote source) remote->warnings)]
    (assoc-in db [:resources/resources resource-id] [:error {source errors}])))

(defmethod yast/event-handler :resources/destroyed
  [db [_ resource-id]]
  (update db :resources/resources dissoc resource-id))

(defmethod yast/event-handler :resources/note-saved
  [db [_ {:notes/keys [context tags]}]]
  (let [tag-status (get-in db [:resources/resources :api.tags/select! 0])
        ctx-status (get-in db [:resources/resources :api.contexts/select! 0])]
    (cond-> db
      (= :success tag-status)
      (update-in [:resources/resources :api.tags/select! 1] into tags)

      (and context (= :success ctx-status))
      (update-in [:resources/resources :api.contexts/select! 1] conj context))))

(defmethod yast/event-handler :forms/created
  [db [_ form-id data opts]]
  (assoc-in db [:forms/forms form-id] (forms/create form-id data opts)))

(defmethod yast/event-handler :forms/destroyed
  [db [_ form-id]]
  (update db :forms/forms dissoc form-id))

(defmethod yast/event-handler :forms/changed
  [db [_ form-id path value]]
  (update-in db [:forms/forms form-id] forms/change path value))

(defmethod yast/event-handler :toasts/hidden
  [db [_ toast-id]]
  (assoc-in db [:toasts/toasts toast-id :state] :hidden))

(defmethod yast/event-handler :toasts/created
  [db [_ toast-id toast]]
  (assoc-in db [:toasts/toasts toast-id] toast))

(defmethod yast/event-handler :toasts/shown
  [db [_ toast-id]]
  (cond-> db
    (get-in db [:toasts/toasts toast-id :state])
    (assoc-in [:toasts/toasts toast-id :state] :visible)))

(defmethod yast/event-handler :toasts/destroyed
  [db [_ toast-id]]
  (update db :toasts/toasts dissoc toast-id))
