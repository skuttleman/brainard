(ns brainard.common.stubs.reagent
  #?(:cljs
     (:require
       [reagent.core :as r])))

(defn create-class [spec]
  #?(:cljs    (r/create-class spec)
     :default (:reagent-render spec)))
