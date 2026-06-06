(ns brainard.infra.store.events
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.pages.note.actions :as-alias note.act]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]))

(defmethod defacto/event-reducer :modals/created
  [db [_ modal-id modal]]
  (assoc-in db [:modals/modals modal-id] modal))

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

(defn ^:private receive [db resource-key val]
  (-> db
      (defacto/event-reducer [::res/submitted resource-key])
      (defacto/event-reducer [::res/succeeded resource-key val])))

(defmethod defacto/event-reducer :api.notes/saved
  [db [_ {:notes/keys [context tags]}]]
  (let [tag-res (defacto/query-responder db [::res/?:resource [::specs/tags#select]])
        ctx-res (defacto/query-responder db [::res/?:resource [::specs/contexts#select]])]
    (cond-> db
      (res/success? tag-res)
      (receive [::specs/tags#select] (into (res/payload tag-res) tags))

      (and context (res/success? ctx-res))
      (receive [::specs/contexts#select] (conj (res/payload ctx-res) context)))))

(defmethod defacto/event-reducer :api.schedules/modified
  [db [_ note-id schedules]]
  (let [key [::note.act/notes#sync note-id]
        res (defacto/query-responder db [::res/?:resource key])]
    (cond-> db
      (res/success? res)
      (receive key (assoc (res/payload res) :notes/schedules schedules)))))
