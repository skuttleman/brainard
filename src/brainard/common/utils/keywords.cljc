(ns brainard.common.utils.keywords)

(defn kw-str [kw]
  (when kw
    (let [ns (namespace kw)]
      (cond->> (name kw)
        ns (str ns "/")))))
