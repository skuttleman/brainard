(ns brainard.common.utils.strings
  "Utilities for operating on strings.")

(defn truncate-to [s max-len]
  (let [last-idx (dec (count s))
        len (min last-idx max-len)]
    (cond-> (subs s 0 len)
      (< len last-idx) (str "..."))))
