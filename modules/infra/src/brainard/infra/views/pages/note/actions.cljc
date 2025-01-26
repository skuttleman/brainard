(ns brainard.infra.views.pages.note.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.fragments.note-edit :as note-edit]
    [brainard.notes.api.specs :as snotes]
    [brainard.schedules.api.specs :as ssched]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(def ^:const update-note-key [::forms+/valid [::notes#update ::forms/edit-note]])
(def ^:const pin-note-key [::forms+/std [::notes#pin ::forms/pin-note]])

(defmethod forms+/re-init ::notes#update [_ _ result] result)
(forms+/validated ::notes#update (valid/->validator snotes/modify)
  [_ {::forms/keys [data] :as spec}]
  (let [note-id (:notes/id data)
        spec (assoc spec :payload data)]
    (specs/with-cbs (res/->request-spec [::specs/notes#modify note-id] spec)
                    :ok-commands [[::res/submit! [::specs/notes#find note-id]]])))

(defmethod forms+/re-init ::notes#pin [_ _ result] (select-keys result #{:notes/id :notes/pinned?}))
(defmethod res/->request-spec ::notes#pin
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload (select-keys data #{:notes/pinned?}))
        note-id (:notes/id data)]
    (specs/with-cbs (res/->request-spec [::specs/notes#modify note-id] spec)
                    :ok-events [[:api.notes/saved]]
                    :ok-commands [[::res/submit! [::specs/notes#find note-id]]]
                    :err-commands [[:toasts/fail!]])))

(defmethod res/->request-spec ::notes#reinstate
  [resource-key {:keys [note] :as spec}]
  (let [note-id (:notes/id note)]
    (specs/with-cbs (res/->request-spec [::specs/notes#modify note-id] (assoc spec :payload note))
                    :ok-events [[::res/destroyed resource-key]]
                    :ok-commands [[:toasts/succeed! {:message "previous version of note was reinstated"}]
                                  [::res/submit! [::specs/notes#find note-id]]
                                  [::res/submit! [::specs/note#history note-id]]]
                    :err-commands [[:toasts/fail!]])))

(forms+/validated ::schedules#create (valid/->validator ssched/create)
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload data)]
    (specs/with-cbs (res/->request-spec [::specs/schedules#create] spec)
                    :ok-events [[:api.schedules/saved (:schedules/note-id data)]]
                    :ok-commands [[:toasts/succeed! {:message "schedule created"}]]
                    :err-commands [[:toasts/fail!]])))

(defmethod res/->request-spec ::schedules#destroy
  [[_ resource-id :as resource-key] spec]
  (specs/with-cbs (res/->request-spec [::specs/schedules#destroy resource-id] spec)
                  :ok-events [[:api.schedules/deleted resource-id (:notes/id spec)]
                              [::res/destroyed resource-key]]
                  :ok-commands [[:toasts/succeed! {:message "schedule deleted"}]]
                  :err-events [[::res/destroyed resource-key]]
                  :err-commands [[:toasts/fail!]]))

(defn ->pin-form-attrs [*:store form+ note-id init-form]
  {:*:store      *:store
   :form+        form+
   :no-buttons?  true
   :no-errors?   true
   :resource-key pin-note-key
   :params       {:ok-events  [[::res/swapped [::specs/notes#find note-id]]]
                  :err-events [[::forms/created pin-note-key init-form]]}})

(defn ->delete-modal [{note-id :notes/id}]
  [:modals/sure?
   {:description  "This note and all related schedules will be deleted"
    :yes-commands [[::res/submit!
                    [::specs/notes#destroy note-id]
                    {:ok-commands  [[:toasts/succeed! {:message "note deleted"}]
                                    [:nav/navigate! {:token :routes.ui/home}]]
                     :err-commands [[:toasts/fail!]]}]]}])

(defn ->edit-modal [{note-id :notes/id :as note}]
  [::note-edit/modal
   {:init         note
    :header       "Edit note"
    :params       {:prev-attachments (:notes/attachments note)
                   :prev-tags        (:notes/tags note)
                   :ok-events        [[::res/swapped [::specs/notes#find note-id]]
                                      [::forms/created pin-note-key]]}
    :resource-key update-note-key}])

(defn ->delete-sched-modal [sched-id note]
  [:modals/sure?
   {:description  "This schedule will be deleted"
    :yes-commands [[::res/submit! [::schedules#destroy sched-id] note]]}])
