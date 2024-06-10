(ns brainard.stubbed.system
  #?(:cljs (:require-macros brainard.stubbed.system))
  (:require
    #?(:clj [duct.core :as duct])
    [integrant.core :as ig]))

(defmacro config [config-file]
  `(let [cfg# ~(duct/read-config (duct/resource config-file))]
     (assoc cfg#
            [:brainard.ds/IDBLogger :brainard.ds/storage-logger]
            {:db-name (ig/ref :cfg.ds/db-name)})))

(defmethod ig/init-key :duct/const
  [_ component]
  component)
