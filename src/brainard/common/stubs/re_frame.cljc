(ns brainard.common.stubs.re-frame
  #?(:cljs
     (:require
       [re-frame.core :as rf])))

(defn dispatch [event]
  #?(:cljs
     (rf/dispatch event)))

(defn dispatch-sync [event]
  #?(:cljs
     (rf/dispatch-sync event)))

(defn subscribe [query]
  #?(:cljs    (rf/subscribe query)
     :default (atom nil)))
