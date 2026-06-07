(ns brainard.infra.views.pages.note.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.fragments.note-edit :as-alias note-edit]
    [brainard.notes.api.specs :as snotes]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(defn ->sync-key [note-id] [::notes#sync note-id])
(defn ->pin-key [note-id] [::forms+/std (->sync-key note-id) [::forms/pin-note]])
(defn ->todo-key [note-id todo-id] [::forms+/std (->sync-key note-id) [::forms/todo todo-id]])
(defn ->edit-key [note-id] [::forms+/valid (->sync-key note-id) [::forms/edit-note]])

(def ^:private modify-note-validator (valid/->validator snotes/modify))
(defmethod forms+/validate ::notes#sync [_ data] (modify-note-validator data))
(defmethod res/->request-spec ::notes#sync
  [[_ note-id] {::forms/keys [data] :keys [note] :as spec}]
  (case (::action spec)
    ::pin (let [spec (assoc spec :payload (select-keys data #{:notes/pinned?}))]
            (specs/with-cbs (res/->request-spec [::specs/notes#modify note-id] spec)
                            :err-commands [[:toasts/fail!]]))
    ::todo (let [spec (assoc spec :payload (-> data
                                               (select-keys #{:notes/todos})
                                               (valid/select-spec-keys snotes/full)))]
             (specs/with-cbs (res/->request-spec [::specs/notes#modify note-id] spec)
                             :err-commands [[:toasts/fail!]]))
    ::edit (let [spec (assoc spec :payload (valid/select-spec-keys data snotes/modify))]
             (specs/with-cbs (res/->request-spec [::specs/notes#modify note-id] spec)
                             :ok-events [[:api.notes/saved]]))
    ::delete (res/->request-spec [::specs/notes#destroy note-id] spec)
    ::reinstate (let [payload (valid/select-spec-keys note snotes/reinstate)]
                  (res/->request-spec [::specs/notes#reinstate note-id]
                                      (assoc spec
                                             :payload payload
                                             :ok-commands [[:toasts/succeed! {:message "previous version of note was reinstated"}]
                                                           [::res/submit! [::note#history note-id]]]
                                             :err-commands [[:toasts/fail!]])))
    (res/->request-spec [::specs/notes#find note-id] spec)))

(defmethod forms+/re-init ::notes#sync [[_ [_ note-id] [res-type child-id]] form result]
  (case res-type
    (::forms/pin-note ::forms/edit-note) result
    ::forms/todo {:notes/id    note-id
                  :notes/todos (->> result
                                    :notes/todos
                                    (filter (comp #{child-id} :todos/id))
                                    (mapv #(select-keys % #{:todos/id :todos/completed?})))}
    (forms/data form)))

(defmethod res/->request-spec ::note#history
  [[_ note-id] spec]
  (res/->request-spec [::specs/note#history note-id] spec))

(defn ->pin-form-attrs
  "Return form attrs for a pin/unpin form integrated with the store and callbacks."
  [*:store form+ {note-id :notes/id :as note}]
  (let [pin-note-key (->pin-key note-id)]
    {:*:store      *:store
     :form+        form+
     :no-buttons?  true
     :no-errors?   true
     :resource-key pin-note-key
     :params       {::action    ::pin
                    :err-events [[::forms/created pin-note-key note]]}}))

(defn ->delete-modal
  "Return modal data for confirming deletion of a note and its schedules."
  [{note-id :notes/id}]
  [:modals/sure?
   {:description   "This note and all related schedules will be deleted"
    :yes-btn-class ["note__confirm-delete"]
    :yes-commands  [[::res/resubmit!
                     (->sync-key note-id)
                     {::action      ::delete
                      :ok-commands  [[:toasts/succeed! {:message "note deleted"}]
                                     [:nav/navigate! {:token :routes.ui/home}]]
                      :err-commands [[:toasts/fail!]]}]]}])

(defn ->edit-modal
  "Return a modal descriptor for editing a note; wires init data and submit callbacks."
  [{note-id :notes/id :as note}]
  [::note-edit/modal
   {:init         note
    :header       "Edit note"
    :params       {::action          ::edit
                   :prev-attachments (:notes/attachments note)
                   :prev-tags        (:notes/tags note)
                   :prev-todos       (:notes/todos note)}
    :resource-key (->edit-key note-id)}])
