(ns brainard.infra.stubs.dom
  "Some cljc-compatible wrappers for DOM inter-op."
  (:require
   #?(:cljs [clojure.string :as string])
   [whet.utils.dom :as wdom]))

(def ^:const window wdom/window)
(def ^:const doc #?(:cljs js/document :default nil))
(defonce ^:private listeners (atom {}))

(def ^:private decode-key
  {"Tab"        :key-codes/tab
   "Enter"      :key-codes/enter
   "Escape"     :key-codes/esc
   " "          :key-codes/space
   " "          :key-codes/space
   "ArrowLeft"  :key-codes/left
   "ArrowUp"    :key-codes/up
   "ArrowRight" :key-codes/right
   "ArrowDown"  :key-codes/down

   "Å"          "a"
   "Î"          "d"
   "Ò"          "l"
   "˜"          "n"
   "ˇ"          "t"})

(defn event->modifiers
  "Return a set of all key modifiers in the event"
  [e]
  (cond-> #{}
    #?@(:cljs
        [(.-altKey e) (conj :alt)
         (.-ctrlKey e) (conj :ctrl)
         (.-metaKey e) (conj :meta)
         (.-shiftKey e) (conj :shift)])))

(defn event->key
  "Return a keyword representing the event's keyCode."
  [e]
  #?(:cljs
     (let [key (some-> e .-key)]
       (string/lower-case (decode-key key key)))))

(def ^{:arglists '([e])} prevent-default! wdom/prevent-default!)
(def ^{:arglists '([e])} stop-propagation! wdom/stop-propagation!)
(def ^{:arglists '([e])} target-value wdom/target-value)
(def ^{:arglists '([e])} target-attr wdom/target-attr)
(def ^{:arglists '([node])} blur! wdom/blur!)
(def ^{:arglists '([node])} click! wdom/click!)
(def ^{:arglists '([node])} focus! wdom/focus!)

(defn query-selector
  "Queries a DOM node for an ancestor by CSS selector"
  ([selector]
   (query-selector doc selector))
  ([node selector]
   #?(:cljs
      (some-> node (.querySelector selector)))))

(defn add-listener!
  "Adds an event listener to a node and stores it. Returns a key which can be used
   to remove it with [[remove-listener!]]. An optional 4th arg will be converted to js
   and passed to `.addEventListener`.

   (add-listener! window :keypress (fn [event] ...) opts)"
  ([node event cb]
   (add-listener! node event cb nil))
  ([node event cb options]
   #?(:cljs
      (let [key (gensym)
            opts (clj->js options)
            listener {::cb    cb
                      ::event event
                      ::node  node
                      ::opts  opts}]
        (.addEventListener node (name event) cb opts)
        (swap! listeners assoc key listener)
        key))))

(defn remove-listener!
  "Removes an event listener by key from a global store.

   (def key (add-listener! window :keypress (fn [event] ...)))
   (remove-listener! key)"
  [key]
  #?(:cljs
     (when-let [{::keys [cb event node opts]} (get @listeners key)]
       (swap! listeners dissoc key)
       (.removeEventListener node (name event) cb opts))))
