(ns brainard.common.utils.colls
  "Utilities for operating on heterogeneous collections (i.e. vectors, lists, and sets).")

(defn wrap-vector [x]
  (cond-> x (not (vector? x)) vector))

(defn wrap-set [x]
  (cond->> x (not (set? x)) hash-set))
