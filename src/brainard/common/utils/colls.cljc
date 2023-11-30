(ns brainard.common.utils.colls)

(defn wrap-vector [x]
  (cond-> x (not (vector? x)) vector))

(defn wrap-set [x]
  (cond->> x (not (set? x)) (conj #{})))
