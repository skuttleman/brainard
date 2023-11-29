(ns brainard.common.utils.colls)

(defn wrap-vector [x]
  (cond-> x (not (vector? x)) vector))
