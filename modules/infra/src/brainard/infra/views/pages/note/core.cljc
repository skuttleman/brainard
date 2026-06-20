(ns brainard.infra.views.pages.note.core
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.views.components.core :as comp]
   [brainard.infra.views.controls.core :as ctrls]
   [brainard.infra.views.fragments.note-components :as note-comp]
   [brainard.infra.views.pages.interfaces :as ipages]
   [brainard.infra.views.pages.note.history :as note.history]
   [brainard.infra.views.pages.note.actions :as note.act]
   [brainard.infra.views.pages.note.schedules :as note.sched]
   [defacto.forms.core :as forms]
   [defacto.forms.plus :as forms+]
   [defacto.resources.core :as res]))

(defn ^:private attachment-list [note]
  (when-let [attachments (not-empty (:notes/attachments note))]
    [:div.flex-grow {:style {:flex-basis "33%"}}
     [note-comp/attachment-list {:label? true
                                 :value  attachments}]]))

(defmethod note-comp/todo-item true
  [{:keys [*:store disabled note-id]} todo]
  (store/with-let [init-form {:notes/id    note-id
                              :notes/todos [(select-keys todo #{:todos/id :todos/completed?})]}
                   form-key (note.act/->todo-key note-id (:todos/id todo))
                   check-path [:notes/todos 0 :todos/completed?]
                   sub:form+ (store/form+-sub *:store form-key init-form)]
    (let [form+ @sub:form+
          value (get-in (forms/data form+) check-path)]
      [:li.todo.layout--room-between
       [ctrls/toggle
        (-> {:*:store  *:store
             :commands [[::forms+/submit!
                         form-key
                         {::note.act/action ::note.act/todo
                          :err-events       [[::forms/changed form-key check-path value]]}]]
             :disabled (or disabled (res/requesting? form+))}
            (ctrls/with-attrs form+ check-path))]
       [:span {:class [(when (:todos/completed? todo)
                         "strikethrough")]}
        (:todos/text todo)]])))

(defn ^:private note-links [note]
  (when-let [links (not-empty (:notes/links note))]
    [:div.flex-grow {:style {:flex-basis "33%"}}
     [note-comp/note-links
      {:label? true
       :value  links}]]))

(defn ^:private todo-list [*:store note disabled?]
  (when-let [todos (not-empty (:notes/todos note))]
    [:div.flex-grow {:style {:flex-basis "33%"}}
     [note-comp/todo-list
      {:*:store  *:store
       :disabled disabled?
       :note-id  (:notes/id note)
       :label?   true
       :value    todos}]]))

(defn ^:private pin-toggle [*:store note disabled?]
  (store/with-let [pin-note-key (note.act/->pin-key (:notes/id note))
                   sub:form+ (store/form+-sub *:store pin-note-key note)]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form (note.act/->pin-form-attrs *:store form+ note)
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small" "note__toggle-pinned"]
                                :disabled (or disabled? (res/requesting? form+))
                                :icon     :paperclip-1
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])))

(defn ^:private schedule-item [sched]
  (when-let [parts (note.sched/schedule-parts sched)]
    (into [:div.flex.layout--room-between]
          (interpose [:span "AND"])
          parts)))

(defn ^:private schedule-list [*:store note scheds disabled?]
  [:div
   (if (seq scheds)
     [:<>
      [:p.schedules__header [:em "Existing schedules"]]
      [:ul.layout--stack-between.schedules__items {:style {:margin-bottom "16px"}}
       (for [{sched-id :schedules/id :as sched} scheds
             :let [modal (note.sched/->delete-sched-modal (:notes/id note) sched-id)]]
         ^{:key sched-id}
         [:li.layout--room-between.layout--align-center.space--left.schedules__item
          [comp/plain-button {:*:store  *:store
                              :class    ["is-danger" "is-light" "is-small" "schedules__delete"]
                              :commands [[:modals/create! modal]]
                              :disabled disabled?}
           [comp/icon :trash-3]]
          [schedule-item sched]])]]
     [:p.schedules__empty [:em "no related schedules"]])])

(defn ^:private schedule-form [{:keys [*:store disabled? sub:form+]}]
  (let [form+ @sub:form+]
    [ctrls/form {:*:store      *:store
                 :class        ["schedule-form"]
                 :disabled     disabled?
                 :form+        form+
                 :horizontal?  true
                 :changed?     (forms/changed? form+)
                 :params       {::note.sched/action ::note.sched/create}
                 :resource-key (forms/id form+)
                 :submit/body  "Save"}
     [ctrls/select (-> {:label    "Day of the month"
                        :*:store  *:store
                        :disabled disabled?}
                       (ctrls/with-attrs form+ [:schedules/day]))
      note.sched/day-options]
     [ctrls/select (-> {:label    "Day of the week"
                        :*:store  *:store
                        :disabled disabled?}
                       (ctrls/with-attrs form+ [:schedules/weekday]))
      note.sched/weekday-options]
     [ctrls/select (-> {:label    "Week of the month"
                        :*:store  *:store
                        :disabled disabled?}
                       (ctrls/with-attrs form+ [:schedules/week-index]))
      note.sched/week-index-options]
     [ctrls/select (-> {:label    "Month of the year"
                        :*:store  *:store
                        :disabled disabled?}
                       (ctrls/with-attrs form+ [:schedules/month]))
      note.sched/month-options]
     [ctrls/datetime (-> {:label    "Earliest Moment"
                          :*:store  *:store
                          :disabled disabled?}
                         (ctrls/with-attrs form+ [:schedules/after-timestamp]))]
     [ctrls/datetime (-> {:label    "Latest Moment"
                          :*:store  *:store
                          :disabled disabled?}
                         (ctrls/with-attrs form+ [:schedules/before-timestamp]))]]))

(defn ^:private schedule-editor [*:store note scheds disabled?]
  (store/with-let [schedule-create-key (note.sched/->create-key (:notes/id note))
                   sub:form+ (store/form+-sub *:store
                                              schedule-create-key
                                              {:schedules/note-id (:notes/id note)}
                                              {:remove-nil? true})]
    [:div.layout--stack-between
     [:div.flex.row
      [:em "Add a schedule: "]
      [:span.space--left [schedule-item (forms/data @sub:form+)]]]
     [schedule-form {:*:store *:store :disabled? disabled? :sub:form+ sub:form+}]
     [schedule-list *:store note scheds disabled?]]))

(defn ^:private note-editor [*:store note disabled?]
  [:<>
   [:div.layout--row
    [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
    [pin-toggle *:store note disabled?]]
   [comp/markdown (:notes/body note)]
   [:div.layout--room-between {:style {:width "100%"}}
    [note-links note]
    [todo-list *:store note disabled?]
    [attachment-list note]]
   [note-comp/tag-list note]
   [:div.layout--space-between
    [:div.button-row
     [comp/plain-button {:*:store  *:store
                         :class    ["is-info" "note__edit-button"]
                         :commands [[:modals/create! (note.act/->edit-modal note)]]
                         :disabled disabled?}
      "Edit"]
     [comp/plain-button {:*:store  *:store
                         :class    ["is-danger" "note__archive-button"]
                         :commands [[:modals/create! (note.act/->archive-modal note)]]
                         :disabled disabled?}
      "Archive note"]]
    [:div.button-row
     [comp/link {:token        :routes.resources/export
                 :class        ["button" "is-light" "note__download-button"]
                 :download     (str "note-" (:notes/id note) ".md")
                 :route-params (select-keys note #{:notes/id})
                 :target       "_blank"}
      [comp/icon :download-1]]
     [comp/plain-button {:*:store  *:store
                         :class    ["is-light" "note__history-button"]
                         :commands [[:modals/create! [::note.history/modal
                                                      {:note note}]]]
                         :disabled disabled?}
      "View history"]]]])

(defn ^:private note-root [opts *:store sub:modals disabled? note]
  (if (and disabled? (seq @sub:modals))
    [comp/spinner opts]
    [note-editor *:store note disabled?]))

(defn ^:private schedule-root [*:store sub:modals disabled? [note scheds]]
  (when (or disabled? (:notes/id note))
    (cond
      (and disabled? (seq @sub:modals))
      [comp/spinner]

      (and (seq scheds) (not (:schedules/id (first scheds))))
      [comp/alert :warn
       [:div "Schedules encountered an error. Try reloading."]]

      :else
      [schedule-editor *:store note scheds disabled?])))

(defn ^:private note-err [_]
  [comp/alert :warn
   [:div
    "Note not found. Try "
    [comp/link {:token :routes.ui/home} "creating one"]
    "."]])

(defn ^:private page [*:store note-id]
  (store/with-let [note-key (note.act/->sync-key note-id)
                   sched-key (note.sched/->sync-key note-id)
                   sub:note (store/res-sub *:store note-key)
                   sub:sched (store/res-sub *:store sched-key)
                   sub:modals (store/subscribe *:store [:modals/?:modals
                                                        (partial remove (comp #{:modals/sure?}
                                                                              first
                                                                              :body))])]
    [:div.layout--stack-between
     [comp/with-resource sub:note
      [note-root {:size :large} *:store sub:modals false]
      note-err
      [note-root {:size :large} *:store sub:modals true]]
     [comp/with-resources [sub:note sub:sched]
      [schedule-root *:store sub:modals false]
      [schedule-root *:store sub:modals false]
      [schedule-root *:store sub:modals true]]]))

(defmethod ipages/page :routes.ui/note
  [*:store {{note-id :notes/id} :route-params}]
  ^{:key note-id}
  [page *:store note-id])
