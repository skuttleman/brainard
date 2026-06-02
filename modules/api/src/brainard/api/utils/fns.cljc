(ns brainard.api.utils.fns
  "Utilities for creating and composing functions.")

(defn apply-all
  "Return a function that applies the same args to all provided non-nil functions."
  [& fns]
  (fn [& args]
    (doseq [f fns
            :when f]
      (apply f args))))

(defn safe-comp
  "Compose functions while ignoring nil values."
  [& fns]
  (apply comp (remove nil? fns)))

(defn smap
  "Map f over xs, passing additional args to f."
  [xs f & args]
  (map #(apply f % args) xs))
