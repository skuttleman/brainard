(ns brainard.schedules.api.relevancy
  (:require
    [cljc.java-time.zoned-date-time :as zdt]
    [cljc.java-time.zone-id :as zi]
    [cljc.java-time.day-of-week :as dow]
    [cljc.java-time.month :as mon]
    [cljc.java-time.instant :as inst])
  #?(:clj
     (:import
       (java.util Date))))

(defn ^:private ->weekday [dt]
  (condp = (zdt/get-day-of-week dt)
    dow/sunday :sunday
    dow/monday :monday
    dow/tuesday :tuesday
    dow/wednesday :wednesday
    dow/thursday :thursday
    dow/friday :friday
    dow/saturday :saturday))

(defn ^:private ->month [dt]
  (condp = (zdt/get-month dt)
    mon/january :january
    mon/february :february
    mon/march :march
    mon/april :april
    mon/may :may
    mon/june :june
    mon/july :july
    mon/august :august
    mon/september :september
    mon/october :october
    mon/november :november
    mon/december :december))

(defn from
  "Given a timestamp returns a map of relevancy characteristics to match against."
  [timestamp]
  (let [timestamp #?(:cljs (.getTime timestamp) :default (.getTime ^Date timestamp))
        dt (zdt/of-instant (inst/of-epoch-milli timestamp) (zi/of "UTC"))
        day (zdt/get-day-of-month dt)]
    {:weekday    (->weekday dt)
     :month      (->month dt)
     :day        day
     :week-index (quot day 7)}))
