(ns brainard.common.store.events
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.resources.specs :as-alias rspecs]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]))

(defmethod defacto/event-reducer :routing/navigated
  [db [_ routing-info]]
  (assoc db :routing/info routing-info))

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
  (let [tag-res (defacto/query-responder db [::res/?:resource ::rspecs/tags#select])
        ctx-res (defacto/query-responder db [::res/?:resource ::rspecs/contexts#select])]
    (cond-> db
      (= :success (:status tag-res))
      (-> (defacto/event-reducer [::res/submitted ::rspecs/tags#select])
          (defacto/event-reducer [::res/succeeded ::rspecs/tags#select
                                  (into (:payload tag-res) tags)]))

      (and context (= :success (:status ctx-res)))
      (-> (defacto/event-reducer [::res/submitted ::rspecs/contexts#select])
          (defacto/event-reducer [::res/succeeded ::rspecs/contexts#select
                                  (conj (:payload ctx-res) context)])))))

(defmethod defacto/event-reducer :api.schedules/saved
  [db [_ note-id sched]]
  (let [note-res (defacto/query-responder db [::res/?:resource [::rspecs/notes#find note-id]])]
    (cond-> db
      (= :success (:status note-res))
      (-> (defacto/event-reducer [::res/submitted [::rspecs/notes#find note-id] (:params note-res)])
          (defacto/event-reducer [::res/succeeded [::rspecs/notes#find note-id]
                                  (update (:payload note-res) :notes/schedules conj sched)])))))

(defmethod defacto/event-reducer :api.schedules/deleted
  [db [_ sched-id note-id]]
  (let [note-res (defacto/query-responder db [::res/?:resource [::rspecs/notes#find note-id]])]
    (cond-> db
      (= :success (:status note-res))
      (-> (defacto/event-reducer [::res/submitted [::rspecs/notes#find note-id] (:params note-res)])
          (defacto/event-reducer [::res/succeeded [::rspecs/notes#find note-id]
                                  (update (:payload note-res) :notes/schedules
                                          (partial remove (comp #{sched-id} :schedules/id)))])))))
