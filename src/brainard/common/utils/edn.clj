(ns brainard.common.utils.edn
  (:refer-clojure :exclude [read read-string])
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string])
  (:import
    (java.io PushbackReader)))

(defn read [stream]
  (let [reader (-> stream
            io/reader
            PushbackReader.)
        byte (.read reader)]
    (when-not (= -1 byte)
      (.unread reader byte)
      (edn/read reader))))

(defn read-string [s]
  (when-not (string/blank? s)
    (edn/read-string s)))

(defn resource [resource-name]
  (-> resource-name
      io/resource
      read))
