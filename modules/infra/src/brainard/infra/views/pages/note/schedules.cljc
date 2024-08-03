(ns brainard.infra.views.pages.note.schedules
  (:require
    [brainard.api.utils.dates :as dates]
    [brainard.infra.store.specs :as-alias specs]
    [clojure.pprint :as pp]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]))

(defn ->sched-create-key [note]
  [::forms+/valid [::specs/schedules#create (:notes/id note)]])

(def ^:const month-options
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

(def ^:const weekday-options
  (into [[nil "(any)"]]
        (map (juxt identity name))
        [:sunday
         :monday
         :tuesday
         :wednesday
         :thursday
         :friday
         :saturday]))

(def ^:const day-options
  (into [[nil "(any)"]]
        (map (juxt identity identity))
        (range 1 32)))

(defn ->radix [v]
  (pp/cl-format nil "~:R" v))

(def ^:const week-index-options
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

(defn schedule-parts [form-data]
  (->> form-data
       (sort-by key)
       (keep ->schedule-part)
       reverse
       seq))

(defn ->delete-sched-modal [sched-id note]
  [:modals/sure?
   {:description  "This schedule will be deleted"
    :yes-commands [[::res/submit! [::specs/schedules#destroy sched-id] note]]}])
