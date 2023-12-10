(ns brainard.common.store.events
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.resources.specs :as-alias rspecs]
    [defacto.core :as defacto]))

(defn ^:private merger [row1 row2]
  (if (and (map? row1) (map? row2))
    (merge-with conj row1 row2)
    (or row2 row1)))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) merger nil warnings))

(defmethod defacto/event-reducer :routing/navigated
  [db [_ routing-info]]
  (assoc db :routing/info routing-info))

(defmethod defacto/event-reducer :resources/submitted
  [db [_ resource-id params]]
  (assoc-in db [:resources/resources resource-id] {:status :requesting
                                                   :params params}))

(defmethod defacto/event-reducer :resources/succeeded
  [db [_ resource-id data]]
  (update-in db [:resources/resources resource-id] assoc
             :status :success
             :payload data))

(defmethod defacto/event-reducer :resources/failed
  [db [_ resource-id source errors]]
  (let [errors (cond-> errors (= :remote source) remote->warnings)]
    (update-in db [:resources/resources resource-id] assoc
               :status :error
               :payload {source errors})))

(defmethod defacto/event-reducer :resources/warned
  [db [_ resource-id response]]
  (let [warnings (remote->warnings response)]
    (update-in db [:resources/resources resource-id] assoc
               :warnings warnings)))

(defmethod defacto/event-reducer :resources/destroyed
  [db [_ resource-id]]
  (cond-> db
    (not (defacto/query-responder db [:app/?:loading]))
    (defacto/event-reducer [:resources/initialized resource-id])))

(defmethod defacto/event-reducer :resources/initialized
  [db [_ resource-id]]
  (update db :resources/resources dissoc resource-id))

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

(defmethod defacto/event-reducer :modals/created
  [db [_ modal-id modal]]
  (update db :modals/modals assoc modal-id modal))

(defmethod defacto/event-reducer :modals/displayed
  [db [_ modal-id]]
  (cond-> db
    (get-in db [:modals/modals modal-id])
    (assoc-in [:modals/modals modal-id :state] :displayed)))

(defmethod defacto/event-reducer :modals/hidden
  [db [_ modal-id]]
  (cond-> db
    (get-in db [:modals/modals modal-id])
    (assoc-in [:modals/modals modal-id :state] :hidden)))

(defmethod defacto/event-reducer :modals/all-hidden
  [db _]
  (update db :modals/modals update-vals #(assoc % :state :hidden)))

(defmethod defacto/event-reducer :modals/destroyed
  [db [_ modal-id]]
  (update db :modals/modals dissoc modal-id))

(defmethod defacto/event-reducer :modals/all-destroyed
  [db _]
  (update db :modals/modals empty))

(defmethod defacto/event-reducer :toasts/created
  [db [_ toast-id toast]]
  (assoc-in db [:toasts/toasts toast-id] toast))

(defmethod defacto/event-reducer :toasts/hidden
  [db [_ toast-id]]
  (cond-> db
    (defacto/query-responder db [:toasts/?:toast toast-id])
    (assoc-in [:toasts/toasts toast-id :state] :hidden)))

(defmethod defacto/event-reducer :toasts/shown
  [db [_ toast-id]]
  (cond-> db
    (defacto/query-responder db [:toasts/?:toast toast-id])
    (assoc-in [:toasts/toasts toast-id :state] :visible)))

(defmethod defacto/event-reducer :toasts/destroyed
  [db [_ toast-id]]
  (update db :toasts/toasts dissoc toast-id))

(defmethod defacto/event-reducer :api.notes/saved
  [db [_ {:notes/keys [context tags]}]]
  (let [tag-res (defacto/query-responder db [:resources/?:resource ::rspecs/tags#select])
        ctx-res (defacto/query-responder db [:resources/?:resource ::rspecs/contexts#select])]
    (cond-> db
      (= :success (:status tag-res))
      (update-in [:resources/resources ::rspecs/tags#select :payload] into tags)

      (and context (= :success (:status ctx-res)))
      (update-in [:resources/resources ::rspecs/contexts#select :payload] conj context))))

(defmethod defacto/event-reducer :api.schedules/saved
  [db [_ note-id sched]]
  (let [note-res (defacto/query-responder db [:resources/?:resource [::rspecs/notes#find note-id]])]
    (cond-> db
      (= :success (:status note-res))
      (update-in [:resources/resources [::rspecs/notes#find note-id] :payload :notes/schedules] conj sched))))

(defmethod defacto/event-reducer :api.schedules/deleted
  [db [_ sched-id note-id]]
  (let [note-res (defacto/query-responder db [:resources/?:resource [::rspecs/notes#find note-id]])]
    (cond-> db
      (= :success (:status note-res))
      (update-in [:resources/resources [::rspecs/notes#find note-id] :payload :notes/schedules]
                 (partial remove (comp #{sched-id} :schedules/id))))))
