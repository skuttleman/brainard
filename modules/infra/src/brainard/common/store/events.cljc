(ns brainard.common.store.events
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.resources.specs :as-alias rspecs]
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
  (cond-> db
    (not (defacto/query-responder db [:app/?:loading]))
    (update :resources/resources dissoc resource-id)))

(defmethod defacto/event-reducer :resources/note-saved
  [db [_ {:notes/keys [context tags]}]]
  (let [[tag-status] (defacto/query-responder db [:resources/?:resource ::rspecs/tags#select])
        [ctx-status] (defacto/query-responder db [:resources/?:resource ::rspecs/contexts#select])]
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
  (cond-> db
    (not (defacto/query-responder db [:app/?:loading]))
    (update :forms/forms dissoc form-id)))

(defmethod defacto/event-reducer :forms/changed
  [db [_ form-id path value]]
  (update-in db [:forms/forms form-id] forms/change path value))

(defmethod defacto/event-reducer :toasts/hidden
  [db [_ toast-id]]
  (cond-> db
    (:state (defacto/query-responder db [:toasts/?:toast toast-id]))
    (assoc-in [:toasts/toasts toast-id :state] :hidden)))

(defmethod defacto/event-reducer :toasts/created
  [db [_ toast-id toast]]
  (assoc-in db [:toasts/toasts toast-id] toast))

(defmethod defacto/event-reducer :toasts/shown
  [db [_ toast-id]]
  (cond-> db
    (:state (defacto/query-responder db [:toasts/?:toast toast-id]))
    (assoc-in [:toasts/toasts toast-id :state] :visible)))

(defmethod defacto/event-reducer :toasts/destroyed
  [db [_ toast-id]]
  (update db :toasts/toasts dissoc toast-id))
