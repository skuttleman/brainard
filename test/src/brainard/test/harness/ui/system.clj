(ns brainard.test.harness.ui.system
  (:require
    [brainard.infra.db.store :as ds]
    [brainard.test.harness.integration.system :as tsys]
    [brainard.test.harness.ui.utils :as tutils]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.test :as t]
    [etaoin.api :as eta]
    [etaoin.impl.util :as ueta]
    [integrant.core :as ig]
    brainard.test))

(defmethod ig/init-key :cfg.test/server-port
  [_ _]
  (ueta/get-free-port))

(defn transact-multi! [db data]
  (if (map? data)
    (->> data
         (sort-by key)
         (into [] (mapcat (fn [[_ tx-data]]
                            (ds/transact! db tx-data)
                            tx-data))))
    (doto data
      (->> (ds/transact! db)))))

(defn safe-screenshot! [driver]
  (try
    (let [filename (-> t/*testing-vars*
                       first
                       str
                       (string/replace #"/" "__")
                       (string/replace #"^#'" "")
                       gensym
                       (str ".png"))]
      (eta/screenshot driver filename))
    (catch Throwable e
      (.printStackTrace e))))

(defn collect-js-coverage! [driver]
  (when (= "true" (System/getenv "JS_COVERAGE"))
    (try
      (let [coverage-json (eta/js-execute driver "return JSON.stringify(window.__coverage__ || null)")
            nyc-output-dir (io/file "target/nyc_output")]
        (when (and coverage-json (not= "null" coverage-json))
          (.mkdirs nyc-output-dir)
          (spit (io/file nyc-output-dir (str (random-uuid) ".json"))
                coverage-json)))
      (catch Throwable _))))

(defn ->driver []
  (let [headless? (= "true" (System/getenv "HEADLESS"))]
    (eta/chrome {:headless    headless?
                 :path-driver (or (System/getenv "CHROMEDRIVER_PATH")
                                  "chromedriver")
                 :args        (into ["--window-size=1200,900"]
                                    (when headless?
                                      ["--no-sandbox"
                                       "--disable-dev-shm-usage"]))})))

(defmacro with-webdriver [[driver-binding base-url-binding seeds] & body]
  (let [db-sym (gensym "db")]
    `(tsys/with-app [{port# :cfg.test/server-port ~db-sym :brainard/IDBConn}
                     {:config    "duct/ui-test.edn"
                      :init-keys [:brainard/webserver]}]
       (let [screenshot?# (= "true" (System/getenv "SCREENSHOT"))
             orig-report# t/report
             ~driver-binding (try (->driver)
                                  (catch Throwable _#
                                    (->driver)))
             ~base-url-binding (str "http://localhost:" port#)
             ~@(for [[sym seed] seeds
                     token [sym `(->> ~seed
                                      (str "seed/")
                                      tutils/edn-fixture
                                      (transact-multi! ~db-sym))]]
                 token)]
         (binding [t/report (fn [event#]
                              (when (and (#{:fail :error} (:type event#))
                                         screenshot?#)
                                (safe-screenshot! ~driver-binding))
                              (orig-report# event#))]
           (try
             ~@body
             (catch Throwable e#
               (when screenshot?#
                 (safe-screenshot! ~driver-binding))
               (throw e#))
             (finally
               (collect-js-coverage! ~driver-binding)
               (eta/quit ~driver-binding))))))))
