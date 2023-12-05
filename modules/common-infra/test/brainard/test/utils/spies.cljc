(ns brainard.test.utils.spies
  (:refer-clojure :exclude [constantly]))

(def ^:private ^:const init-state
  {:calls []})

(defn on [f]
  (let [state (atom init-state)]
    (with-meta (fn [& args]
                 (swap! state update :calls conj (vec args))
                 (apply f args))
               {::state state})))

(defn constantly
  ([]
   (constantly nil))
  ([value]
   (on (clojure.core/constantly value))))

(defn calls [spy]
  (when-let [state (::state (meta spy))]
    (:calls @state)))

(defn wipe! [spy]
  (when-let [state (::state (meta spy))]
    (reset! state init-state)))
