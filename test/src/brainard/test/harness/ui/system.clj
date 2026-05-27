(ns brainard.test.harness.ui.system
  (:require
    [brainard.infra.db.store :as ds]
    [brainard.test.harness.integration.system :as tsys]
    [brainard.test.harness.ui.utils :as tutils]
    [etaoin.api :as eta]))

(defn transact-multi! [db data]
  (if (map? data)
    (->> data
         (sort-by key)
         (into [] (mapcat (fn [[_ tx-data]]
                            (ds/transact! db tx-data)
                            tx-data))))
    (doto data
      (->> (ds/transact! db)))))

(defmacro with-webdriver [[driver-binding base-url-binding seeds] & body]
  (let [db-sym (gensym "db")]
    `(tsys/with-app [{port# :cfg/server-port ~db-sym :brainard/IDBConn}
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
             ~@(for [[sym seed] seeds
                     token [sym `(->> ~seed
                                      (str "seed/")
                                      tutils/edn-fixture
                                      (transact-multi! ~db-sym))]]
                 token)]
         (try
           ~@body
           (finally
             (eta/quit ~driver-binding)))))))
