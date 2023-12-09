(ns brainard.api.schedules.relevancy
  (:import
    (java.time DayOfWeek Month ZoneId ZonedDateTime)
    (java.util Date)))

(defn ^:private ->weekday [^ZonedDateTime dt]
  (condp = (.getDayOfWeek dt)
    DayOfWeek/SUNDAY :sunday
    DayOfWeek/MONDAY :monday
    DayOfWeek/TUESDAY :tuesday
    DayOfWeek/WEDNESDAY :wednesday
    DayOfWeek/THURSDAY :thursday
    DayOfWeek/FRIDAY :friday
    DayOfWeek/SATURDAY :saturday))

(defn ^:private ->month [^ZonedDateTime dt]
  (condp = (.getMonth dt)
    Month/JANUARY :january
    Month/FEBRUARY :february
    Month/MARCH :march
    Month/APRIL :april
    Month/MAY :may
    Month/JUNE :june
    Month/JULY :july
    Month/AUGUST :august
    Month/SEPTEMBER :september
    Month/OCTOBER :october
    Month/NOVEMBER :november
    Month/DECEMBER :december))

(defn ^:private ->day [^ZonedDateTime dt]
  (.getDayOfMonth dt))

(defn from
  "Given a timestamp returns a map of relevancy characteristics to match against."
  [^Date timestamp]
  (let [dt (ZonedDateTime/ofInstant (.toInstant timestamp) (ZoneId/systemDefault))
        day (->day dt)]
    {:weekday    (->weekday dt)
     :month      (->month dt)
     :day        day
     :week-index (quot day 7)}))
