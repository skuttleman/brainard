(ns brainard.api.utils.keywords
  "Utilities for operating on keywords."
  (:refer-clojure :exclude [str]))

(defn str
  "Return the string representation of a keyword, including its namespace if present."
  [kw]
  (when kw
    (let [ns (namespace kw)]
      (cond->> (name kw)
        ns (clojure.core/str ns "/")))))
