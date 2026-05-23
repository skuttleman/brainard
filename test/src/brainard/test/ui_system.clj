(ns brainard.test.ui-system
  (:require
    [brainard.test.system :as tsys]
    [etaoin.api :as eta]))

(defmacro with-system [[driver-binding base-url-binding] & body]
  `(tsys/with-system [{port# :cfg/server-port} {:config    "duct/ui-test.edn"
                                                :init-keys [:brainard/webserver]}]
     (let [headless?# (= "true" (System/getenv "HEADLESS"))
           ~base-url-binding (str "http://localhost:" port#)
           ~driver-binding (eta/chrome {:headless    headless?#
                                        :path-driver (or (System/getenv "CHROMEDRIVER_PATH") "chromedriver")
                                        :args        (when headless?#
                                                       ["--no-sandbox"
                                                        "--disable-dev-shm-usage"])})]
       (try
         ~@body
         (finally
           (eta/quit ~driver-binding))))))
