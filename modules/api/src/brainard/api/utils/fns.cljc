(ns brainard.api.utils.fns
  "Utilities for creating and composing functions.")

(defn apply-all [& fns]
  (fn [& args]
    (doseq [f fns
            :when f]
      (apply f args))))

(defn safe-comp [& fns]
  (apply comp (remove nil? fns)))

(defn smap [xs f & args]
  (map #(apply f % args) xs))
