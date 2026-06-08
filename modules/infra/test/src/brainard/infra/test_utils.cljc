(ns brainard.infra.test-utils
  #?(:cljs (:require-macros brainard.infra.test-utils))
  (:require
    #?(:clj [clojure.core.async :as async])
    clojure.test))

(defmacro async [cb & body]
  (if (:ns &env)
    `(clojure.test/async ~cb ~@body)
    `(let [prom# (promise)
           ~cb #(deliver prom# nil)]
       ~@body
       (when (= ::timeout (deref prom# 5000 ::timeout))
         (throw (ex-info "failed to complete async test within timeout" {}))))))

#?(:clj
   (defn <!!
     ([ch]
      (<!! ch 500))
     ([ch ms]
      (<!! ch ms ::timeout))
     ([ch ms or-else]
      (async/alt!! ch ([v] v)
                   (async/timeout ms) or-else))))

(defn spy [f]
  (let [state (atom {::calls []})]
    (with-meta (fn [& args]
                 (swap! state update ::calls conj args)
                 (apply f args))
               {::state state})))

(defn calls [spy]
  (-> spy meta ::state deref ::calls))

(defn called-with? [spy args]
  (boolean (first (filter #{args} (calls spy)))))
