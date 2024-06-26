(ns brainard.infra.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.views.fragments.note-edit :as note-edit]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [brainard.schedules.infra.views :as sched.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(def ^:private ^:const update-note-key [::forms+/std [::specs/notes#update ::forms/edit-note]])
(def ^:private ^:const pin-note-key [::forms+/std [::specs/notes#pin ::forms/pin-note]])

(defn ^:private tag-list [note]
  (if-let [tags (not-empty (:notes/tags note))]
    [comp/tag-list {:value tags}]
    [:em "no tags"]))

(defn ^:private pin-toggle [*:store note]
  (r/with-let [init-form (select-keys note #{:notes/id :notes/pinned?})
               sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! pin-note-key init-form])
                             (store/subscribe [::forms+/?:form+ pin-note-key]))]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form {:*:store      *:store
                    :form+        form+
                    :no-buttons?  true
                    :no-errors?   true
                    :resource-key pin-note-key
                    :params       {:ok-events  [[::res/swapped [::specs/notes#find (:notes/id note)]]]
                                   :err-events [[::forms/created pin-note-key init-form]]}}
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small"]
                                :disabled (res/requesting? form+)
                                :icon     :paperclip
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms+/destroyed pin-note-key]))))

(defmethod icomp/modal-header ::history
  [_ _]
  "Note's change history")

(defmethod icomp/modal-body ::history
  [*:store {{note-id :notes/id} :note}]
  (r/with-let [spec [::specs/note#history note-id]
               sub:history (-> *:store
                               (store/dispatch! [::res/submit! spec])
                               (store/subscribe [::res/?:resource spec]))
               sub:recon (store/subscribe *:store [:notes.history/?:reconstruction spec])]
    [comp/with-resource sub:history [notes.views/note-history *:store @sub:recon]]
    (finally
      (store/emit! *:store [::res/destroyed spec]))))

(defn ^:private ->delete-modal [{note-id :notes/id}]
  [:modals/sure?
   {:description  "This note and all related schedules will be deleted"
    :yes-commands [[::res/submit!
                    [::specs/notes#destroy note-id]
                    {:ok-commands [[:nav/navigate! {:token :routes.ui/home}]]}]]}])

(defn ^:private ->edit-modal [{note-id :notes/id :as note}]
  [::note-edit/modal
   {:init         note
    :header       "Edit note"
    :params       {:prev-tags (:notes/tags note)
                   :ok-events [[::res/swapped [::specs/notes#find note-id]]
                               [::forms/created pin-note-key]]}
    :resource-key update-note-key}])

(defn ^:private root [*:store note]
  (r/with-let [delete-modal (->delete-modal note)
               edit-modal (->edit-modal note)
               history-modal [::history {:note note}]]
    [:div.layout--stack-between
     [:div.layout--row
      [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
      [pin-toggle *:store note]]
     [comp/markdown (:notes/body note)]
     [tag-list note]
     [:div.layout--space-between
      [:div.button-row
       [comp/plain-button {:*:store  *:store
                           :class    ["is-info"]
                           :commands [[:modals/create! edit-modal]]}
        "Edit"]
       [comp/plain-button {:*:store  *:store
                           :class    ["is-danger"]
                           :commands [[:modals/create! delete-modal]]}
        "Delete note"]]
      [comp/plain-button {:*:store  *:store
                          :class    ["is-light"]
                          :commands [[:modals/create! history-modal]]}
       "View history"]]
     [sched.views/schedule-editor *:store note]]))

(defmethod ipages/page :routes.ui/note
  [*:store {:keys [route-params]}]
  (let [resource-key [::specs/notes#find (:notes/id route-params)]]
    (r/with-let [sub:note (-> *:store
                              (store/dispatch! [::res/ensure! resource-key])
                              (store/subscribe [::res/?:resource resource-key]))]
      (let [resource @sub:note]
        (cond
          (res/success? resource)
          [root *:store (res/payload resource)]

          (res/error? resource)
          [comp/alert :warn
           [:div
            "Note not found. Try "
            [comp/link {:token :routes.ui/home} "creating one"]
            "."]]

          :else
          [comp/spinner]))
      (finally
        (store/emit! *:store [::res/destroyed resource-key])))))
