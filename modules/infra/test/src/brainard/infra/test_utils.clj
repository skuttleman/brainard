(ns brainard.infra.test-utils)

(defn spy [f]
  (let [state (atom {::calls []})]
    (with-meta (fn [& args]
                 (swap! state update ::calls conj args)
                 (apply f args))
               {::state state})))

(defn calls [spy]
  (-> spy meta ::state deref ::calls))
