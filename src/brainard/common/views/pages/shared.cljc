(ns brainard.common.views.pages.shared
  (:require
    [brainard.common.utils.dates :as dates]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.components.core :as comp]
    [clojure.pprint :as pp]))

(defn search-results [{:keys [anchor]} notes]
  (if-not (seq notes)
    [:span.search-results
     [comp/alert :info "No search results"]]
    [:ul.search-results
     (for [{:notes/keys [id context body tags]} notes]
       ^{:key id}
       [:li {:id    id
             :class [(when (= anchor (str id)) "anchored")]}
        [:div.layout--row
         [:strong.layout--no-shrink context]
         [:span.flex-grow.space--left.truncate
          body]
         [:a.link.space--left {:href (rte/path-for :routes.ui/note {:notes/id id})}
          "view"]]
        [:div.flex
         [comp/tag-list {:value (take 8 tags)}]
         (when (< 8 (count tags))
           [:em.space--left "more..."])]])]))

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
