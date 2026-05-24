(ns brainard.test.ui-system
  (:require
    [brainard.infra.db.store :as ds]
    [brainard.test.system :as tsys]
    [brainard.test.ui.utils :as ui-utils]
    [etaoin.api :as eta]))

(defmacro with-system [[driver-binding base-url-binding bnds] & body]
  (let [db-sym (gensym "db")]
    `(tsys/with-system [{port# :cfg/server-port ~db-sym :brainard/IDBConn}
                        {:config    "duct/ui-test.edn"
                         :init-keys [:brainard/webserver]}]
       (let [headless?# (= "true" (System/getenv "HEADLESS"))
             ~driver-binding (eta/chrome {:headless    headless?#
                                          :path-driver (or (System/getenv "CHROMEDRIVER_PATH")
                                                           "chromedriver")
                                          :args        (when headless?#
                                                         ["--no-sandbox"
                                                          "--disable-dev-shm-usage"])})
             ~base-url-binding (str "http://localhost:" port#)
             ~@(for [[sym seed] bnds
                     token [sym `(cond-> ~seed
                                   (string? ~seed) (->> (str "seed/")
                                                        ui-utils/edn-fixture)
                                   true (doto (->> (ds/transact! ~db-sym))))]]
                 token)]
         (try
           ~@body
           (finally
             (eta/quit ~driver-binding)))))))
