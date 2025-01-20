(ns brainard.infra.utils.edn
  "Utilities for reading EDN."
  (:refer-clojure :exclude [read read-string])
  (:require
    #?(:clj [clojure.java.io :as io])
    [clojure.edn :as edn*]
    [clojure.string :as string])
  #?(:clj
     (:import
       (java.io PushbackReader))))

#?(:clj
   (defn read [stream]
     (with-open [reader (-> stream
                            io/reader
                            PushbackReader.)]
       (let [byte (.read reader)]
         (when-not (= -1 byte)
           (.unread reader byte)
           (edn*/read reader))))))

(defn read-string [s]
  (when-not (string/blank? s)
    (edn*/read-string s)))
