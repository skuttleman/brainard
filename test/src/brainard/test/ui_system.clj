(ns brainard.test.ui-system
  (:require
    [brainard.infra.db.store :as ds]
    [brainard.infra.utils.edn :as edn]
    [brainard.test.system :as tsys]
    [clojure.java.io :as io]
    [etaoin.api :as eta]))

(defmacro with-system [[driver-binding base-url-binding {:keys [seed]}] & body]
  (let [seed (cond-> seed
               (string? seed) (->> (str "fixtures/seed/")
                                   io/resource
                                   slurp
                                   edn/read-string))]
    `(tsys/with-system [{port# :cfg/server-port db# :brainard/IDBConn}
                        {:config    "duct/ui-test.edn"
                         :init-keys [:brainard/webserver]}]
       (let [headless?# (= "true" (System/getenv "HEADLESS"))
             ~base-url-binding (str "http://localhost:" port#)
             ~driver-binding (eta/chrome {:headless    headless?#
                                          :path-driver (or (System/getenv "CHROMEDRIVER_PATH")
                                                           "chromedriver")
                                          :args        (when headless?#
                                                         ["--no-sandbox"
                                                          "--disable-dev-shm-usage"])})]
         (try
           (ds/transact! db# ~seed)
           ~@body
           (finally
             (eta/quit ~driver-binding)))))))

(defn fill-form! [driver form-selector field-vals]
  (eta/wait-visible driver {:css form-selector})
  (doseq [[label value] field-vals
          :let [xpath (format "//input[@id=//label[text()='%s']/@for]" label)
                el (eta/query driver {:xpath xpath})]]
    (eta/clear-el driver el)
    (eta/fill-el driver el value)))

(defn submit-form! [driver form-selector field-vals]
  (fill-form! driver form-selector field-vals)
  (eta/click driver {:css (str form-selector " button.submit")}))
