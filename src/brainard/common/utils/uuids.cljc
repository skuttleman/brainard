(ns brainard.common.utils.uuids
  #?(:clj
     (:require
       [clj-uuid :as uuid])))

(def ^:const regex #"(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

(defn random []
  #?(:clj     (uuid/squuid)
     :default (random-uuid)))

(defn to-uuid [x]
  #?(:clj  (uuid/as-uuid x)
     :cljs (cond-> x (not (uuid? x)) uuid)))
