(ns brainard.common.store.events
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.specs :as rspecs]
    [defacto.core :as defacto]))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) (partial merge-with conj) nil warnings))

(defmethod defacto/event-reducer :routing/navigated
  [db [_ routing-info]]
  (assoc db :routing/info routing-info))

(defmethod defacto/event-reducer :resources/submitted
  [db [_ resource-id]]
  (assoc-in db [:resources/resources resource-id] [:requesting]))

(defmethod defacto/event-reducer :resources/succeeded
  [db [_ resource-id data]]
  (assoc-in db [:resources/resources resource-id] [:success data]))

(defmethod defacto/event-reducer :resources/failed
  [db [_ resource-id source errors]]
  (let [errors (cond-> errors (= :remote source) remote->warnings)]
    (assoc-in db [:resources/resources resource-id] [:error {source errors}])))

(defmethod defacto/event-reducer :resources/destroyed
  [db [_ resource-id]]
  (update db :resources/resources dissoc resource-id))

(defmethod defacto/event-reducer :resources/note-saved
  [db [_ {:notes/keys [context tags]}]]
  (let [tag-status (get-in db [:resources/resources ::rspecs/tags#select 0])
        ctx-status (get-in db [:resources/resources ::rspecs/contexts#select 0])]
    (cond-> db
      (= :success tag-status)
      (update-in [:resources/resources ::rspecs/tags#select 1] into tags)

      (and context (= :success ctx-status))
      (update-in [:resources/resources ::rspecs/contexts#select 1] conj context))))

(defmethod defacto/event-reducer :forms/created
  [db [_ form-id data opts]]
  (assoc-in db [:forms/forms form-id] (forms/create form-id data opts)))

(defmethod defacto/event-reducer :forms/destroyed
  [db [_ form-id]]
  (update db :forms/forms dissoc form-id))

(defmethod defacto/event-reducer :forms/changed
  [db [_ form-id path value]]
  (update-in db [:forms/forms form-id] forms/change path value))

(defmethod defacto/event-reducer :toasts/hidden
  [db [_ toast-id]]
  (assoc-in db [:toasts/toasts toast-id :state] :hidden))

(defmethod defacto/event-reducer :toasts/created
  [db [_ toast-id toast]]
  (assoc-in db [:toasts/toasts toast-id] toast))

(defmethod defacto/event-reducer :toasts/shown
  [db [_ toast-id]]
  (cond-> db
    (get-in db [:toasts/toasts toast-id :state])
    (assoc-in [:toasts/toasts toast-id :state] :visible)))

(defmethod defacto/event-reducer :toasts/destroyed
  [db [_ toast-id]]
  (update db :toasts/toasts dissoc toast-id))
