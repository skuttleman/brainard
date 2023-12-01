(ns brainard.common.utils.maps
  "Utilities for operating on maps."
  #?(:cljs (:require-macros brainard.common.utils.maps))
  (:refer-clojure :exclude [flatten]))

(defn ^:private flatten* [m path]
  (mapcat (fn [[k v]]
            (let [path' (conj path k)]
              (if (map? v)
                (flatten* v path')
                [[path' v]])))
          m))

(defn flatten [m]
  "Flattens nested maps into a map of \"paths\".

   (flatten {:a {:b 1 :c {:d [{:x :y}]}}})
   ;; =>  {[:a :b] 1 [:a :c :d] [{:x :y}]}"
  (into {} (flatten* m [])))

(defn nest [m]
  "Given a map of vector \"paths\" -> values, creates nested maps.

   (nest {[:a :b] 1 [:a :c :d] [{:x :y}]})
   ;; =>  {:a {:b 1 :c {:d [{:x :y}]}}}"
  (reduce-kv assoc-in {} m))

(defn assoc-defaults [m & kvs]
  "Assoc values onto a map when the existing value is missing or nil."
  (into (or m {})
        (comp (partition-all 2)
              (remove (comp some? (partial get m) first)))
        kvs))

(defmacro m
  "Generates a map literal by keying symbols off a keyword representation. At runtime,
   the symbols will be evaluated as normal.
   All other `forms` are [[conj]]'d onto the map before returning.

   (let [a 1 b 2 c 3]
     (m a [:b b] c {:d 4 :e 5}))
   ;; => {:a 1 :b 2 :c 3 :d 4 :e 5"
  [& forms]
  (loop [m {}
         [form :as forms] forms]
    (cond
      (empty? forms) m
      (symbol? form) (recur (assoc m (keyword form) form) (next forms))
      :else (recur (conj m form) (next forms)))))
