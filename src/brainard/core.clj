(ns brainard.core
  (:require
    [brainard.infra.system :as sys]
    [duct.core :as duct]
    [integrant.core :as ig]))

(defn -main [& _]
  (let [system (sys/start! "duct.edn")]
    (duct/await-daemons system)))

(comment
  (def system (sys/start! "duct.edn"))
  (ig/halt! system))
