(ns brainard.schedules.infra.views
  (:require
    [brainard.api.utils.dates :as dates]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.reagent :as r]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [clojure.pprint :as pp]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]))

(defn ^:private ^:const ->sched-create-key [note] [::forms+/valid [::specs/schedules#create (:notes/id note)]])

(def ^:private ^:const month-options
  (into [[nil "(any)"]]
        (map (juxt identity name))
        [:january
         :february
         :march
         :april
         :may
         :june
         :july
         :august
         :september
         :october
         :november
         :december]))

(def ^:private ^:const weekday-options
  (into [[nil "(any)"]]
        (map (juxt identity name))
        [:sunday
         :monday
         :tuesday
         :wednesday
         :thursday
         :friday
         :saturday]))

(def ^:private ^:const day-options
  (into [[nil "(any)"]]
        (map (juxt identity identity))
        (range 1 32)))

(defn ^:private ->radix [v]
  (pp/cl-format nil "~:R" v))

(def ^:private ^:const week-index-options
  (into [[nil "(any)"]]
        (map (juxt identity #(str (->radix (inc %)) " week")))
        (range 5)))

(defn ^:private ->schedule-part [[k v]]
  (case k
    :schedules/weekday [:<>
                        [:span "on a"]
                        [:em.blue (name v)]]

    :schedules/month [:<>
                      [:span "during"]
                      [:em.blue (name v)]]

    :schedules/day [:<>
                    [:span "on the"]
                    [:em.blue (->radix v)]
                    [:span "day of the month"]]

    :schedules/week-index [:<>
                           [:span "during the"]
                           [:em.blue (->radix (inc v))]
                           [:span "week of the month"]]
    :schedules/before-timestamp [:<>
                                 [:span [:em.blue "before"]]
                                 [:span (dates/to-iso-datetime-min-precision v)]]
    :schedules/after-timestamp [:<>
                                [:span [:em.blue "after"]]
                                [:span (dates/to-iso-datetime-min-precision v)]]
    nil))

(defn ^:private schedule-display [form-data]
  (when-let [parts (seq (->> form-data
                             (sort-by key)
                             reverse
                             (keep ->schedule-part)
                             (interpose [:span "AND"])))]
    (into [:div.flex.layout--room-between] parts)))

(defn ^:private ul [*:store note]
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

(defn ^:private form [{:keys [*:store sub:form+]}]
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
      day-options]
     [ctrls/select (-> {:label   "Day of the week"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/weekday]))
      weekday-options]
     [ctrls/select (-> {:label   "Week of the month"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/week-index]))
      week-index-options]
     [ctrls/select (-> {:label   "Month of the year"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:schedules/month]))
      month-options]
     [ctrls/datetime (-> {:label   "Earliest Moment"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:schedules/after-timestamp]))]
     [ctrls/datetime (-> {:label   "Latest Moment"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:schedules/before-timestamp]))]]))

(defn editor [*:store note]
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
               [form {:*:store *:store :sub:form+ sub:form+}]
               [ul *:store note]]
    (finally
      (store/emit! *:store [::forms+/destroyed schedule-create-key]))))
