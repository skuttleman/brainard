(ns brainard.infra.views.pages.note.actions
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.fragments.note-edit :as note-edit]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(def ^:const update-note-key [::forms+/std [::notes#update ::forms/edit-note]])
(def ^:const pin-note-key [::forms+/std [::notes#pin ::forms/pin-note]])

(defmethod forms+/re-init ::notes#update [_ _ result] result)
(defmethod res/->request-spec ::notes#update
  [_ {::forms/keys [data] :as spec}]
  (res/->request-spec [::specs/notes#patch] (assoc spec :note data)))

(defmethod forms+/re-init ::notes#pin [_ _ result] (select-keys result #{:notes/id :notes/pinned?}))
(defmethod res/->request-spec ::notes#pin
  [_ {::forms/keys [data] :as spec}]
  (merge (res/->request-spec [::specs/notes#patch] (assoc spec :note data))
         {:body         (select-keys data #{:notes/pinned?})
          :ok-events    [[:api.notes/saved]]
          :ok-commands  []
          :err-commands [[:toasts/fail!]]}))

(defmethod res/->request-spec ::notes#reinstate
  [resource-key {:keys [note] :as spec}]
  (let [note-id (:notes/id note)]
    (merge (res/->request-spec [::specs/notes#patch] spec)
           {:ok-events    [[::res/destroyed resource-key]]
            :ok-commands  [[:toasts/succeed! {:message "previous version of note was reinstated"}]
                           [::res/re-succeed! [::specs/notes#find note-id]]
                           [::res/submit! [::specs/note#history note-id]]]
            :err-commands [[:toasts/fail!]]})))

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
                    {:ok-commands [[:toasts/succeed! {:message "note deleted"}]
                                   [:nav/navigate! {:token :routes.ui/home}]]
                     :err-commands [[:toasts/fail!]]}]]}])

(defn ->edit-modal [{note-id :notes/id :as note}]
  [::note-edit/modal
   {:init         note
    :header       "Edit note"
    :params       {:prev-tags (:notes/tags note)
                   :ok-events [[::res/swapped [::specs/notes#find note-id]]
                               [::forms/created pin-note-key]]}
    :resource-key update-note-key}])

(defn ->delete-sched-modal [sched-id note]
  [:modals/sure?
   {:description  "This schedule will be deleted"
    :yes-commands [[::res/submit! [::specs/schedules#destroy sched-id] note]]}])
