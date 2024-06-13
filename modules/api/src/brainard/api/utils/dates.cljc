(ns brainard.api.utils.dates)

(defn ^:private pad-date-segment [num]
  (str (when (< num 10) \0) num))

(defn to-iso-datetime-min-precision [date]
  #?(:cljs
     (when date
       (str (.getFullYear date)
            \- (pad-date-segment (inc (.getMonth date)))
            \- (pad-date-segment (.getDate date))
            \T (pad-date-segment (.getHours date))
            \: (pad-date-segment (.getMinutes date))))))

(def ^{:arglists '([date])} ->str
  (let [#?@(:cljs [formatter (js/Intl.DateTimeFormat. "en-US"
                                                      #js {:dateStyle "full"
                                                           :timeStyle "short"})])]
    (fn [date]
      #?(:cljs
         (.format formatter date)))))