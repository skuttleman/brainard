(ns brainard.common.utils.keywords
  (:refer-clojure :exclude [str]))

(defn str [kw]
  (when kw
    (let [ns (namespace kw)]
      (cond->> (name kw)
        ns (clojure.core/str ns "/")))))
