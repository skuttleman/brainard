(ns brainard.common.stubs.reagent
  (:refer-clojure :exclude [atom])
  #?(:cljs
     (:require-macros
       brainard.common.stubs.reagent))
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
