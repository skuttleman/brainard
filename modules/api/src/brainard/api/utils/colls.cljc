(ns brainard.api.utils.colls
  "Utilities for operating on heterogeneous collections (i.e. vectors, lists, and sets).")

(defn wrap-vector
  "Ensure x is a vector, wraps in a vector if not already one."
  [x]
  (cond-> x (not (vector? x)) vector))

(defn wrap-set
  "Ensure x is a set, wraps in a set if not already one."
  [x]
  (cond->> x (not (set? x)) hash-set))
