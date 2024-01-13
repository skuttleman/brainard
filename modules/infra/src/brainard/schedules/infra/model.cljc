(ns brainard.schedules.infra.model
  (:require
    [brainard.api.utils.dates :as dates]
    [clojure.pprint :as pp]))

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

(defn ^:private ->radix [v]
  (pp/cl-format nil "~:R" v))

(def ^:const week-index-options
  (into [[nil "(any)"]]
        (map (juxt identity #(str (->radix (inc %)) " week")))
        (range 5)))

(defn ->schedule-part [[k v]]
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
