(ns brainard.infra.stubs.reagent
  "Stubs the cljs react wrapper, reagent, for cljc compatibility."
  #?(:cljs (:require-macros brainard.infra.stubs.reagent))
  (:refer-clojure :exclude [atom])
  (:require
    [reagent.core :as r*]))

(def ^{:arglists '([spec])} create-class
  #?(:cljs    r*/create-class
     :default :reagent-render))

(defn atom [value]
  #?(:cljs    (r*/atom value)
     :default (clojure.core/atom value)))

(defmacro with-let [bindings & body]
  (let [final-form (last body)
        [body fin] (if (and (list? final-form) (= 'finally (first final-form)))
                     [(butlast body) (rest final-form)]
                     [body nil])]
    (if (:ns &env)
      `(r*/with-let ~bindings
         ~@body
         ~(list 'finally
                `(do ~@fin)))
      `(let ~bindings (try ~@body)))))
