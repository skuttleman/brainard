(ns brainard.infra.views.pages.note.core
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.infra.views.pages.note.history :as note.history]
    [brainard.infra.views.pages.note.specs :as note.specs]
    [brainard.schedules.infra.views :as sched.views]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defn ^:private tag-list [note]
  (if-let [tags (not-empty (:notes/tags note))]
    [comp/tag-list {:value tags}]
    [:em "no tags"]))

(defn ^:private pin-toggle [*:store note]
  (r/with-let [init-form (select-keys note #{:notes/id :notes/pinned?})
               sub:form+ (store/form+-sub *:store note.specs/pin-note-key init-form)]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form (note.specs/pin-form-attrs *:store form+ (:notes/id note) init-form)
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small"]
                                :disabled (res/requesting? form+)
                                :icon     :paperclip
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms+/destroyed note.specs/pin-note-key]))))

(defn ^:private root [*:store note]
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
                         :commands [[:modals/create! (note.specs/->edit-modal note)]]}
      "Edit"]
     [comp/plain-button {:*:store  *:store
                         :class    ["is-danger"]
                         :commands [[:modals/create! (note.specs/->delete-modal note)]]}
      "Delete note"]]
    [comp/plain-button {:*:store  *:store
                        :class    ["is-light"]
                        :commands [[:modals/create! [::note.history/modal {:note note}]]]}
     "View history"]]
   [sched.views/schedule-editor *:store note]])

(defmethod ipages/page :routes.ui/note
  [*:store {{note-id :notes/id} :route-params}]
  (let [resource-key [::specs/notes#find note-id]]
    (r/with-let [sub:note (store/res-sub *:store resource-key)]
      (let [resource @sub:note]
        (cond
          (res/success? resource)
          ^{:key note-id}
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
