(ns brainard.common.utils.maps
  (:refer-clojure :exclude [flatten]))

(defn ^:private flatten* [m path]
  (mapcat (fn [[k v]]
            (let [path' (conj path k)]
              (if (map? v)
                (flatten* v path')
                [[path' v]])))
          m))

(defn flatten [m]
  "Given a map m with potentially nested maps, flatten into a top level map where all the
   keys are vectors representing the path into the original map"
  (into {} (flatten* m [])))

(defn nest [m]
  "Given a map where all of its keys represent a path into a nested data structure,
   generated the representational data structure"
  (reduce-kv assoc-in {} m))

(defn assoc-defaults [m & kvs]
  "Assoc values onto a map when the existing value is missing or nil"
  (into (or m {})
        (comp (partition-all 2)
              (remove (comp some? (partial get m) first)))
        kvs))
