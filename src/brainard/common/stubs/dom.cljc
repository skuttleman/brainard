(ns brainard.common.stubs.dom
  "Some cljc-compatible wrappers for DOM inter-op."
  (:require
    [brainard.common.utils.edn :as edn]
    [clojure.set :as set]))

(def ^:const window
  #?(:cljs js/window :default nil))

(def ^:const env
  #?(:cljs    {:init-db (-> window .-BRAINARD_INITIAL_DB edn/read-string)}
     :default {}))

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
  (doto e
    #?(:cljs
       (some-> .preventDefault))))

(defn stop-propagation! [e]
  (doto e
    #?(:cljs
       (some-> .stopPropagation))))

(defn target-value [e]
  #?(:cljs
     (some-> e .-target .-value)))

(defn blur! [node]
  (doto node
    #?(:cljs
       (some-> .blur))))

(defn focus! [node]
  (doto node
    #?(:cljs
       (some-> .focus))))

(defn event->key [e]
  #?(:cljs
     (some-> e .-keyCode code->key)))

(defn add-listener!
  "Adds an event listener to a node and stores it. Returns a key which can be used
   to remove it with [[remove-listener]]. An optional 4th arg will be converted to js
   and passed to `.addEventListener`.

   (add-listener! window :keypress (fn [event] ...) opts)"
  ([node event cb]
   (add-listener! node event cb nil))
  ([node event cb options]
   #?(:cljs
      (let [key (gensym)
            id (.addEventListener node (name event) cb (clj->js options))
            listener {::id    id
                      ::node  node
                      ::event event}]
        (swap! listeners assoc key listener)
        key))))

(defn remove-listener!
  "Removes an event listener by key from a global store.

   (def key (add-listener! window :keypress (fn [event] ...)))
   (remove-listener! key)"
  [key]
  #?(:cljs
     (when-let [{::keys [node event id]} (get @listeners key)]
       (swap! listeners dissoc key)
       (try
         (.removeEventListener node (name event) id)
         (catch :default _)))))
