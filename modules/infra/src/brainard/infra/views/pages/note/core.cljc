(ns brainard.infra.views.pages.note.core
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.fragments.note-components :as note-comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.infra.views.pages.note.history :as note.history]
    [brainard.infra.views.pages.note.actions :as note.act]
    [brainard.infra.views.pages.note.schedules :as note.sched]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defn ^:private attachment-list [note]
  (when-let [attachments (not-empty (:notes/attachments note))]
    [note-comp/attachment-list {:label? true
                                :value  attachments}]))

(defmethod note-comp/todo-item true
  [{:keys [*:store disabled note-id]} todo]
  (r/with-let [init-form {:notes/id    note-id
                          :notes/todos [(select-keys todo #{:todos/id :todos/completed?})]}
               form-key (note.act/->todo-key note-id (:todos/id todo))
               check-path [:notes/todos 0 :todos/completed?]
               sub:form+ (store/form+-sub *:store form-key init-form)]
    (let [form+ @sub:form+]
      [:li.todo.layout--room-between
       [:input.checkbox
        {:checked   (boolean (:todos/completed? todo))
         :type      :checkbox
         :value     (get-in (forms/data form+) check-path)
         :disabled  (or disabled (res/requesting? form+))
         :on-change (fn [e]
                      (-> *:store
                          (store/emit! [::forms/changed
                                        form-key
                                        check-path
                                        (= "false" (dom/target-value e))])
                          (store/dispatch! [::forms+/resubmit!
                                            form-key
                                            {::note.act/action ::note.act/todo
                                             :err-events       [[::forms/created form-key init-form]]}])))}]

       [:span {:class [(when (:todos/completed? todo)
                         "strikethrough")]}
        (:todos/text todo)]])
    (finally
      (store/emit! *:store [::forms/destroyed form-key]))))

(defn ^:private todo-list [*:store note disabled?]
  (when-let [todos (not-empty (:notes/todos note))]
    [note-comp/todo-list
     {:*:store   *:store
      :note-id   (:notes/id note)
      :label?    true
      :value     todos
      :disabled disabled?}]))

(defn ^:private pin-toggle-disabled [note]
  [:div
   [:form.form {:disabled true}
    [ctrls/icon-toggle {:class    ["is-small" "note__toggle-pinned"]
                        :disabled true
                        :icon     :paperclip
                        :type     :submit
                        :value    (:notes/pinned? note)}]]])

(defn ^:private pin-toggle [*:store note]
  (r/with-let [pin-note-key (note.act/->pin-key (:notes/id note))
               sub:form+ (store/form+-sub *:store pin-note-key note)]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form (note.act/->pin-form-attrs *:store form+ note)
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small" "note__toggle-pinned"]
                                :disabled (res/requesting? form+)
                                :icon     :paperclip
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms/destroyed pin-note-key]))))

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
      [:ul.layout--stack-between.schedules__items
       (for [{sched-id :schedules/id :as sched} scheds
             :let [modal (note.sched/->delete-sched-modal (:notes/id note) sched-id)]]
         ^{:key sched-id}
         [:li.layout--room-between.layout--align-center.space--left.schedules__item
          [comp/plain-button {:*:store  *:store
                              :class    ["is-danger" "is-light" "is-small" "schedules__delete"]
                              :commands [[:modals/create! modal]]
                              :disabled disabled?}
           [comp/icon :trash-can]]
          [schedule-item sched]])]]
     [:p.schedules__empty [:em "no related schedules"]])])

(defn ^:private schedule-form [{:keys [*:store disabled? sub:form+]}]
  (let [form+ @sub:form+]
    [ctrls/form {:*:store      *:store
                 :class        ["schedule-form"]
                 :form+        form+
                 :horizontal?  true
                 :changed?     (forms/changed? form+)
                 :disabled     disabled?
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
  (r/with-let [schedule-create-key (note.sched/->create-key (:notes/id note))
               sub:form+ (store/form+-sub *:store
                                          schedule-create-key
                                          {:schedules/note-id (:notes/id note)}
                                          {:remove-nil? true})]
    [:div.layout--stack-between
     [:div.flex.row
      [:em "Add a schedule: "]
      [:span.space--left [schedule-item (forms/data @sub:form+)]]]
     [schedule-form {:*:store *:store :disabled? disabled? :sub:form+ sub:form+}]
     [schedule-list *:store note scheds disabled?]]
    (finally
      (store/emit! *:store [::forms/destroyed schedule-create-key]))))

(defn ^:private note-editor [*:store disabled? note]
  [:<>
   [:div.layout--row
    [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
    (if disabled?
      [pin-toggle-disabled note]
      [pin-toggle *:store note])]
   [comp/markdown (:notes/body note)]
   [:div.layout--room-between {:style {:width "100%"}}
    [:div.flex-grow {:style {:flex-basis "50%"}}
     [todo-list *:store note disabled?]]
    [:div.flex-grow {:style {:flex-basis "50%"}}
     [attachment-list note]]]
   [note-comp/tag-list note]
   [:div.layout--space-between
    [:div.button-row
     [comp/plain-button {:*:store  *:store
                         :class    ["is-info" "note__edit-button"]
                         :commands [[:modals/create! (note.act/->edit-modal note)]]
                         :disabled disabled?}
      "Edit"]
     [comp/plain-button {:*:store  *:store
                         :class    ["is-danger" "note__delete-button"]
                         :commands [[:modals/create! (note.act/->delete-modal note)]]
                         :disabled disabled?}
      "Delete note"]]
    [comp/plain-button {:*:store  *:store
                          :class    ["is-light" "note__history-button"]
                          :commands [[:modals/create! [::note.history/modal
                                                       {:note note}]]]
                          :disabled disabled?}
       "View history"]]])

(defn ^:private note-root [_ *:store disabled? note]
  [note-editor *:store disabled? note])

(defn ^:private schedule-root [*:store disabled? [note scheds]]
  ^{:key (str (:notes/id note) "-sched-" disabled?)}
  [schedule-editor *:store note scheds disabled?])

(defn ^:private note-err [_]
  [comp/alert :warn
   [:div
    "Note not found. Try "
    [comp/link {:token :routes.ui/home} "creating one"]
    "."]])

(defn ^:private page [*:store note-id]
  (r/with-let [note-key (note.act/->sync-key note-id)
               sched-key (note.sched/->sync-key note-id)
               sub:note (store/res-sub *:store note-key)
               sub:sched (store/res-sub *:store sched-key)
               sched-err (constantly nil)]
    [:div.layout--stack-between
     [comp/with-resource* sub:note
      [note-root {:size :large} *:store false]
      note-err
      [note-root {} *:store true]]
     [comp/with-resources* [sub:note sub:sched]
      [schedule-root *:store false]
      sched-err
      [schedule-root *:store true]]]
    (finally
      (-> *:store
          (store/emit! [::res/destroyed sched-key])
          (store/emit! [::res/destroyed note-key])))))

(defmethod ipages/page :routes.ui/note
  [*:store {{note-id :notes/id} :route-params}]
  ^{:key note-id}
  [page *:store note-id])
