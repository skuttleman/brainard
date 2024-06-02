(ns brainard.api.storage.interfaces
  (:refer-clojure :exclude [read]))

(defprotocol IRead
  "Extensible read operations"
  :extend-via-metadata true
  (read [this input]
    "Read from a resource"))

(defprotocol IWrite
  "Extensible write operations"
  :extend-via-metadata true
  (write! [this input]
    "Write to a resource"))

(defmulti ^{:arglists '([params])} ->input
          "Convert params into read or write operation input"
          :brainard.api.storage.core/type)
