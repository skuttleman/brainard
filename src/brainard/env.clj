(ns brainard.env
  (:refer-clojure :exclude [get])
  (:require
   [duct.core.env :as duct.env]))

(defn get
  ([name]
   (get name 'Str))
  ([name type-or-default]
   (let [[type default] (if (symbol? type-or-default)
                          [type-or-default (if (= type-or-default 'Bool) false nil)]
                          ['Str type-or-default])]
     (get name type default)))
  ([name type default]
   (duct.env/env name type :or default)))

(defmacro with-env [env & body]
  `(binding [duct.env/*env* (merge duct.env/*env* ~env)]
     ~@body))
