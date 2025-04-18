(ns brainard.infra.views.pages.note.core
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.infra.views.pages.note.actions :as note.act]
    [brainard.infra.views.pages.note.history :as note.history]
    [brainard.infra.views.pages.note.schedules :as note.sched]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defn ^:private tag-list [note]
  (if-let [tags (not-empty (:notes/tags note))]
    [comp/tag-list {:value tags}]
    [:em "no tags"]))

(defn ^:private attachment-list [note]
  (when-let [attachments (not-empty (:notes/attachments note))]
    [comp/attachment-list {:label? true
                           :value attachments}]))

(defn ^:private todo-list [note]
  (when-let [todos (not-empty (:notes/todos note))]
    [comp/todo-list {:label? true
                     :value  todos}]))

(defn ^:private pin-toggle [*:store note]
  (r/with-let [init-form (select-keys note #{:notes/id :notes/pinned?})
               sub:form+ (store/form+-sub *:store note.act/pin-note-key init-form)]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form (note.act/->pin-form-attrs *:store form+ (:notes/id note) init-form)
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small"]
                                :disabled (res/requesting? form+)
                                :icon     :paperclip
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms+/destroyed note.act/pin-note-key]))))

(defn ^:private schedule-item [sched]
  (when-let [parts (note.sched/schedule-parts sched)]
    (into [:div.flex.layout--room-between]
          (interpose [:span "AND"])
          parts)))

(defn ^:private schedule-list [*:store note]
  [:div
   (if-let [scheds (seq (:notes/schedules note))]
     [:<>
      [:p [:em "Existing schedules"]]
      [:ul.layout--stack-between
       (for [{sched-id :schedules/id :as sched} scheds
             :let [modal (note.act/->delete-sched-modal sched-id note)]]
         ^{:key sched-id}
         [:li.layout--room-between.layout--align-center.space--left
          [comp/plain-button {:*:store *:store
                              :class    ["is-danger" "is-light" "is-small"]
                              :commands [[:modals/create! modal]]}
           [comp/icon :trash-can]]
          [schedule-item sched]])]]
     [:p [:em "no related schedules"]])])

(defn ^:private schedule-form [{:keys [*:store sub:form+]}]
  (let [form+ @sub:form+]
    [ctrls/form {:*:store      *:store
                 :form+        form+
                 :horizontal?  true
                 :changed?     (forms/changed? form+)
                 :resource-key (forms/id form+)
                 :submit/body  "Save"}
     [ctrls/select (-> {:label   "Day of the month"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/day]))
      note.sched/day-options]
     [ctrls/select (-> {:label   "Day of the week"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/weekday]))
      note.sched/weekday-options]
     [ctrls/select (-> {:label   "Week of the month"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/week-index]))
      note.sched/week-index-options]
     [ctrls/select (-> {:label   "Month of the year"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/month]))
      note.sched/month-options]
     [ctrls/datetime (-> {:label   "Earliest Moment"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:schedules/after-timestamp]))]
     [ctrls/datetime (-> {:label   "Latest Moment"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:schedules/before-timestamp]))]]))

(defn ^:private schedule-editor [*:store note]
  (r/with-let [schedule-create-key (note.sched/->sched-create-key note)
               sub:form+ (store/form+-sub *:store
                                          schedule-create-key
                                          {:schedules/note-id (:notes/id note)}
                                          {:remove-nil? true})]
    [:div.layout--stack-between
     [:div.flex.row
      [:em "Add a schedule: "]
      [:span.space--left [schedule-item (forms/data @sub:form+)]]]
     [schedule-form {:*:store *:store :sub:form+ sub:form+}]
     [schedule-list *:store note]]
    (finally
      (store/emit! *:store [::forms+/destroyed schedule-create-key]))))

(defn ^:private root [*:store note]
  [:div.layout--stack-between
   [:div.layout--row
    [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
    [pin-toggle *:store note]]
   [comp/markdown (:notes/body note)]
   [:div.layout--room-between
    [todo-list note]
    [attachment-list note]]
   [tag-list note]
   [:div.layout--space-between
    [:div.button-row
     [comp/plain-button {:*:store  *:store
                         :class    ["is-info"]
                         :commands [[:modals/create! (note.act/->edit-modal note)]]}
      "Edit"]
     [comp/plain-button {:*:store  *:store
                         :class    ["is-danger"]
                         :commands [[:modals/create! (note.act/->delete-modal note)]]}
      "Delete note"]]
    [comp/plain-button {:*:store  *:store
                        :class    ["is-light"]
                        :commands [[:modals/create! [::note.history/modal {:note note}]]]}
     "View history"]]
   [schedule-editor *:store note]])

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
