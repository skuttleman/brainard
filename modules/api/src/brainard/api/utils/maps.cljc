(ns brainard.api.utils.maps
  "Utilities for operating on maps.")

(defn assoc-defaults
  "Assoc values onto a map when the existing value is missing or nil."
  [m & kvs]
  (into (or m {})
        (comp (partition-all 2)
              (remove (comp some? (partial get m) first)))
        kvs))

(defn update-when
  "Only applies update when `k` exists in `m`."
  [m k f & f-args]
  (if (get m k)
    (apply update m k f f-args)
     m))

(defn deep-merge
  "Deeply merges nested maps. Other values are overwritten as with merge"
  [m1 m2]
  (cond->> m2
    (and (map? m1) (map? m2))
    (merge-with deep-merge m1)))
