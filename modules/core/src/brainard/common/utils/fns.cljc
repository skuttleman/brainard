(ns brainard.common.utils.fns
  "Utilities for creating and composing functions."
  #?(:cljs (:require-macros brainard.common.utils.fns))
  (:refer-clojure :exclude [and or]))

(defn apply-all! [& fns]
  (fn [& args]
    (doseq [f fns
            :when f]
      (apply f args))))
