(ns brainard.common.utils.fns
  #?(:cljs (:require-macros brainard.common.utils.fns))
  (:refer-clojure :exclude [and or]))

(defn apply-all! [& fns]
  (fn [& args]
    (doseq [f fns
            :when f]
      (apply f args))))

(defmacro => [& forms]
  `(fn [arg#]
     (-> arg# ~@forms)))

(defn or [& values]
  (boolean (loop [[value :as values] values]
             (cond
               (empty? values) nil
               value value
               :else (recur (next values))))))

(defn and [& values]
  (boolean (loop [[value & values] values]
             (cond
               (empty? values) value
               (not value) value
               :else (recur values)))))
