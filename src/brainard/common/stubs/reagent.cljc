(ns brainard.common.stubs.reagent
  (:refer-clojure :exclude [atom])
  #?(:cljs
     (:require-macros
       brainard.common.stubs.reagent))
  (:require [reagent.core :as r*]))

(defn create-class [spec]
  #?(:cljs    (r*/create-class spec)
     :default (:reagent-render spec)))

(defn atom [value]
  #?(:cljs    (r*/atom value)
     :default (clojure.core/atom value)))

(defmacro with-let [bindings & body]
  (if (:ns &env)
    `(r*/with-let ~bindings ~@body)
    `(let ~bindings (try ~@body))))
