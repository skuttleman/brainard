(ns brainard.common.stubs.dom
  (:require
    [clojure.set :as set]))

(def ^:private key->code
  {:key-codes/tab   9
   :key-codes/esc   27
   :key-codes/enter 13
   :key-codes/up    38
   :key-codes/down  40})

(def ^:private code->key
  (set/map-invert key->code))

(defn prevent-default! [e]
  #?(:cljs
     (doto e
       (some-> .preventDefault))))

(defn stop-propagation! [e]
  #?(:cljs
     (doto e
       (some-> .stopPropagation))))

(defn target-value [e]
  #?(:cljs
     (some-> e .-target .-value)))

(defn click! [node]
  #?(:cljs
     (doto node
       (some-> .click))))

(defn blur! [node]
  #?(:cljs
     (doto node
       (some-> .blur))))

(defn focus! [node]
  #?(:cljs
     (doto node
       (some-> .focus))))

(defn event->key [e]
  #?(:cljs
     (some-> e .-keyCode code->key)))
