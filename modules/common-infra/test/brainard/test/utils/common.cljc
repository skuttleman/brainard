(ns brainard.test.utils.common
  #?(:cljs (:require-macros brainard.test.utils.common)))

(defmacro async [cb & body]
  (if (:ns &env)
    `(clojure.test/async ~cb ~@body)
    `(let [prom# (promise)
           ~cb (fn [] (deliver prom# nil))
           {result# :result ex# :ex} (try {:result (do ~@body)}
                                          (catch Throwable ex#
                                            {:ex ex#}))]
       @prom#
       (some-> ex# throw)
       result#)))
