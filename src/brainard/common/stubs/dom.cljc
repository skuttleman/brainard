(ns brainard.common.stubs.dom
  (:require
    [clojure.set :as set]))

(defonce ^:private listeners (atom {}))

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

(defn add-listener
  ([node event cb]
   (add-listener node event cb nil))
  ([node event cb options]
   #?(:cljs
      (let [key (gensym)
            listener {::id    (.addEventListener node (name event) cb (clj->js options))
                      ::node  node
                      ::event event}]
        (swap! listeners assoc key listener)
        key))))

(defn remove-listener [key]
  #?(:cljs
     (when-let [{::keys [node event id]} (get @listeners key)]
       (swap! listeners dissoc key)
       (try
         (.removeEventListener node (name event) id)
         (catch :default _)))))
