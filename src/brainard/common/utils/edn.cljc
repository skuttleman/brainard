(ns brainard.common.utils.edn
  (:refer-clojure :exclude [read read-string])
  #?(:clj
     (:require
       [clojure.java.io :as io]
       [clojure.edn :as edn*]
       [clojure.string :as string]))
  #?(:clj
     (:import
       (java.io PushbackReader))))

#?(:clj
   (defn read [stream]
     (let [reader (-> stream
                      io/reader
                      PushbackReader.)
           byte (.read reader)]
       (when-not (= -1 byte)
         (.unread reader byte)
         (edn*/read reader)))))

#?(:clj
   (defn read-string [s]
     (when-not (string/blank? s)
       (edn*/read-string s))))

#?(:clj
   (defn resource [resource-name]
     (-> resource-name
         io/resource
         read)))
