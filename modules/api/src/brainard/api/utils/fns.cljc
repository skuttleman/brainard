(ns brainard.api.utils.fns
  "Utilities for creating and composing functions.")

(defn apply-all [& fns]
  (fn [& args]
    (doseq [f fns
            :when f]
      (apply f args))))
