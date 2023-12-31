(ns brainard.common.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.common.store.specs :as-alias specs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [brainard.common.views.pages.shared :as spages]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]))

(def ^:private ^:const form-id ::forms/edit-note)
(def ^:private ^:const update-note-key [::forms+/std [::specs/notes#update form-id]])
(defn ^:private ^:const ->sched-create-key [note] [::forms+/valid [::specs/schedules#create (:notes/id note)]])

(defn ^:private tag-editor [{:keys [*:store sub:form+ sub:tags note]}]
  (let [form+ @sub:form+]
    [ctrls/form {:*:store      *:store
                 :form+        form+
                 :params       {:note   note
                                :fetch? true}
                 :resource-key update-note-key
                 :submit/body  "Save"
                 :buttons      [[:button.button.is-cancel
                                 {:on-click (fn [e]
                                              (dom/prevent-default! e)
                                              (store/emit! *:store
                                                           [::forms/created update-note-key
                                                            (select-keys note #{:notes/tags})]))}
                                 "Cancel"]]}
     [ctrls/tags-editor (-> {:*:store   *:store
                             :form-id   [::tags form-id]
                             :label     "Tags"
                             :sub:items sub:tags}
                            (ctrls/with-attrs form+ [:notes/tags]))]]))

(defn ^:private tag-list [*:store note]
  [:div.layout--space-between
   (if-let [tags (not-empty (:notes/tags note))]
     [comp/tag-list {:value tags}]
     [:em "no tags"])
   [:button.button.is-info
    {:disabled #?(:clj true :default false)
     :on-click (fn [_]
                 (store/emit! *:store [::forms/changed update-note-key [::editing?] true]))}
    "edit stags"]])

(defn ^:private schedule-display [form-data]
  (when-let [parts (seq (->> form-data
                             (sort-by key)
                             reverse
                             (keep spages/->schedule-part)
                             (interpose [:span "AND"])))]
    (into [:div.flex.layout--room-between] parts)))

(defn ^:private schedules-list [*:store note]
  [:div
   (if-let [scheds (seq (:notes/schedules note))]
     [:<>
      [:p [:em "Existing schedules"]]
      [:ul.layout--stack-between
       (for [{sched-id :schedules/id :as sched} scheds
             :let [modal [:modals/sure?
                          {:description  "This schedule will be deleted"
                           :yes-commands [[::res/submit! [::specs/schedules#destroy sched-id] note]]}]]]
         ^{:key sched-id}
         [:li.layout--room-between.layout--align-center.space--left
          [comp/plain-button {:class    ["is-danger" "is-light" "is-small"]
                              :on-click (fn [_]
                                          (store/dispatch! *:store [:modals/create! modal]))}
           [comp/icon :trash]]
          [schedule-display sched]])]]
     [:p [:em "no related schedules"]])])

(defn ^:private schedules-form [{:keys [*:store sub:form+]}]
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
      spages/day-options]
     [ctrls/select (-> {:label   "Day of the week"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/weekday]))
      spages/weekday-options]
     [ctrls/select (-> {:label   "Week of the month"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/week-index]))
      spages/week-index-options]
     [ctrls/select (-> {:label   "Month of the year"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/month]))
      spages/month-options]
     [ctrls/datetime (-> {:label   "Earliest Moment"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:schedules/after-timestamp]))]
     [ctrls/datetime (-> {:label   "Latest Moment"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:schedules/before-timestamp]))]]))

(defn ^:private schedules-editor [*:store note]
  (r/with-let [schedule-create-key (->sched-create-key note)
               sub:form+ (do (store/dispatch! *:store [::forms/ensure!
                                                       schedule-create-key
                                                       {:schedules/note-id (:notes/id note)}
                                                       {:remove-nil? true}])
                             (store/subscribe *:store [::forms+/?:form+ schedule-create-key]))]
    [:div.layout--stack-between
     [:div.flex.row
      [:em "Add a schedule: "]
      [:span.space--left [schedule-display (forms/data @sub:form+)]]]
     [schedules-form {:*:store *:store :sub:form+ sub:form+}]
     [schedules-list *:store note]]
    (finally
      (store/emit! *:store [::forms+/destroyed schedule-create-key]))))

(defn ^:private root* [{:keys [*:store]} [note]]
  (r/with-let [init-form (select-keys note #{:notes/tags})
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! update-note-key init-form])
                             (store/subscribe *:store [::forms+/?:form+ update-note-key]))
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])]
    [:div.layout--stack-between
     [:h1 [:strong (:notes/context note)]]
     [:p (:notes/body note)]
     (if (::editing? (forms/data @sub:form+))
       [tag-editor {:*:store   *:store
                    :sub:form+ sub:form+
                    :sub:tags  sub:tags
                    :note      note}]
       [tag-list *:store note])
     [schedules-editor *:store note]]
    (finally
      (store/emit! *:store [::forms+/destroyed update-note-key]))))

(defmethod ipages/page :routes.ui/note
  [{:keys [route-params *:store]}]
  (let [resource-key [::specs/notes#find (:notes/id route-params)]]
    (r/with-let [sub:note (do (store/dispatch! *:store [::res/ensure! resource-key])
                              (store/subscribe *:store [::res/?:resource resource-key]))]
      [comp/with-resources [sub:note] [root* {:*:store *:store}]]
      (finally
        (store/emit! *:store [::res/destroyed resource-key])))))
