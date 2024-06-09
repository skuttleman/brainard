(ns brainard.resources.system
  #?(:cljs (:require-macros brainard.resources.system))
  (:require
    #?(:clj [duct.core :as duct])
    [integrant.core :as ig]))

#?(:clj
   (def cfg
     (duct/read-config (duct/resource "duct/base.edn"))))

(defmacro config []
  `(let [cfg# ~cfg]
     (assoc cfg#
            [:brainard.ds/IDBLogger :brainard.ds/storage-logger]
            {:db-name (ig/ref :cfg.ds/db-name)})))

(defmethod ig/init-key :duct/const
  [_ component]
  component)
