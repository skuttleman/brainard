(ns brainard.common.stubs.reagent
  "Stubs the cljs react wrapper, reagent, for cljc compatibility."
  #?(:cljs (:require-macros brainard.common.stubs.reagent))
  (:refer-clojure :exclude [atom])
  (:require
    [reagent.core :as r*]))

(def ^{:arglists '([spec])} create-class
  #?(:cljs    r*/create-class
     :default :reagent-render))

(defn atom [value]
  #?(:cljs    (r*/atom value)
     :default (clojure.core/atom value)))

(defmacro with-let [bindings & body]
  (if (:ns &env)
    `(r*/with-let ~bindings ~@body)
    `(let ~bindings (try ~@body))))
