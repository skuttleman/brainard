(ns brainard.api.utils.dates)

(defn ^:private pad-date-segment [num]
  (str (when (< num 10) \0) num))

#?(:cljs
   (def ^:private formatter
     (js/Intl.DateTimeFormat. "en-US"
                              #js {:dateStyle "full"
                                   :timeStyle "short"})))

(defn to-iso-datetime-min-precision
  "Format a JS Date to an ISO-like string with minute precision (cljs)."
  [date]
  #?(:cljs
     (when date
       (str (.getFullYear date)
            \- (pad-date-segment (inc (.getMonth date)))
            \- (pad-date-segment (.getDate date))
            \T (pad-date-segment (.getHours date))
            \: (pad-date-segment (.getMinutes date))))))

(defn ->str
  "Formats a JS Date in a human-readable format"
  [date]
  #?(:cljs
     (.format formatter date)))
