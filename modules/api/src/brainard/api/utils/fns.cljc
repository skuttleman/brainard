(ns brainard.api.utils.fns
  "Utilities for creating and composing functions."
  #?(:cljs (:require-macros brainard.api.utils.fns))
  (:refer-clojure :exclude [and or]))

(defn apply-all! [& fns]
  (fn [& args]
    (doseq [f fns
            :when f]
      (apply f args))))
