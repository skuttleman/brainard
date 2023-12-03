(ns brainard.common.store.events
  (:require
    [brainard.common.forms.core :as forms]
    [yast.core :as yast]))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) (partial merge-with conj) nil warnings))

(defmethod yast/event-handler :routing/navigate
  [db [_ routing-info]]
  (assoc db :routing/info routing-info))

(defmethod yast/event-handler :resources/submit
  [db [_ resource-id]]
  (assoc-in db [:resources/resources resource-id] [:requesting]))

(defmethod yast/event-handler :resources/succeeded
  [db [_ resource-id data]]
  (assoc-in db [:resources/resources resource-id] [:success data]))

(defmethod yast/event-handler :resources/failed
  [db [_ resource-id source errors]]
  (let [errors (cond-> errors (= :remote source) remote->warnings)]
    (assoc-in db [:resources/resources resource-id] [:error {source errors}])))

(defmethod yast/event-handler :resources/destroy
  [db [_ resource-id]]
  (update db :resources/resources dissoc resource-id))

(defmethod yast/event-handler :resources.tags/from-note
  [db [_ {:notes/keys [tags]}]]
  (let [status (get-in db [:resources/resources :api.tags/select 0])]
    (cond-> db
      (= :success status)
      (update-in [:resources/resources :api.tags/select 1] into tags))))

(defmethod yast/event-handler :resources.contexts/from-note
  [db [_ {:notes/keys [context]}]]
  (let [status (get-in db [:resources/resources :api.contexts/select 0])]
    (cond-> db
      (and context (= :success status))
      (update-in [:resources/resources :api.contexts/select 1] conj context))))

(defmethod yast/event-handler :forms/create
  [db [_ form-id data opts]]
  (assoc-in db [:forms/forms form-id] (forms/create form-id data opts)))

(defmethod yast/event-handler :forms/destroy
  [db [_ form-id]]
  (update db :forms/forms dissoc form-id))

(defmethod yast/event-handler :forms/change
  [db [_ form-id path value]]
  (update-in db [:forms/forms form-id] forms/change path value))

(defmethod yast/event-handler :toasts/hide
  [db [_ toast-id]]
  (assoc-in db [:toasts/toasts toast-id :state] :hidden))

(defmethod yast/event-handler :toasts/create
  [db [_ toast-id toast]]
  (assoc-in db [:toasts/toasts toast-id] toast))

(defmethod yast/event-handler :toasts/show
  [db [_ toast-id]]
  (cond-> db
    (get-in db [:toasts/toasts toast-id :state])
    (assoc-in [:toasts/toasts toast-id :state] :visible)))

(defmethod yast/event-handler :toasts/destroy
  [db [_ toast-id]]
  (update db :toasts/toasts dissoc toast-id))
