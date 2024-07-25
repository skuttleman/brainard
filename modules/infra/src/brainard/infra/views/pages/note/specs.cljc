(ns brainard.infra.views.pages.note.specs
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.fragments.note-edit :as note-edit]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(def ^:const update-note-key [::forms+/std [::specs/notes#update ::forms/edit-note]])
(def ^:const pin-note-key [::forms+/std [::specs/notes#pin ::forms/pin-note]])

(defmethod forms+/re-init ::specs/notes#pin [_ _ result] (select-keys result #{:notes/id :notes/pinned?}))
(defmethod res/->request-spec ::specs/notes#pin
  [_ {::forms/keys [data] :as spec}]
  (merge (res/->request-spec [::specs/notes#update] spec)
         {:body        (select-keys data #{:notes/pinned?})
          :ok-commands []}))

(defmethod res/->request-spec ::specs/notes#reinstate
  [resource-key {:keys [note] :as spec}]
  (let [note-id (:notes/id note)]
    (merge (res/->request-spec [::specs/notes#update] (assoc spec ::forms/data note))
           {:ok-events   [[::res/destroyed resource-key]]
            :ok-commands [[:toasts/succeed! {:message "previous version of note was reinstated"}]
                          [::res/re-succeed! [::specs/notes#find note-id]]
                          [::res/submit! [::specs/note#history note-id]]]})))

(defn pin-form-attrs [*:store form+ note-id init-form]
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
                    {:ok-commands [[:nav/navigate! {:token :routes.ui/home}]]}]]}])

(defn ->edit-modal [{note-id :notes/id :as note}]
  [::note-edit/modal
   {:init         note
    :header       "Edit note"
    :params       {:prev-tags (:notes/tags note)
                   :ok-events [[::res/swapped [::specs/notes#find note-id]]
                               [::forms/created pin-note-key]]}
    :resource-key update-note-key}])
