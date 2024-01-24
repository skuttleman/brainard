(ns brainard.storage.interfaces
  (:refer-clojure :exclude [read]))

(defprotocol IRead
  ""
  :extend-via-metadata true
  (read [this input]))

(defprotocol IWrite
  ""
  :extend-via-metadata true
  (write! [this input]))

(defmulti ^{:arglists '([params])} ->input
          ""
          :brainard.storage.core/type)
