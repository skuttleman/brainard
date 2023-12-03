(ns user
  #?(:cljs (:require-macros user)))

(defmacro spy [form]
  `(let [{result# :result ex# :ex} (try
                                     {:result ~form}
                                     (catch ~(if (:ns &env) :default 'Throwable) ex#
                                       {:ex ex#}))]
     (println "***SPY***")
     (println '~form "=>" (or ex# result#) \newline)
     (some-> ex# throw)
     result#))
