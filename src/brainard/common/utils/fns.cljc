(ns brainard.common.utils.fns)

(defn apply-all! [& fns]
  (fn [& args]
    (doseq [f fns :when f]
      (apply f args))))
