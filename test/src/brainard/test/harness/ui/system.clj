(ns brainard.test.harness.ui.system
  (:require
    [brainard.infra.db.store :as ds]
    [brainard.test.harness.integration.system :as tsys]
    [brainard.test.harness.ui.utils :as tutils]
    [cheshire.core :as json]
    [clojure.string :as string]
    [clojure.test :as t]
    [etaoin.api :as eta]
    [etaoin.impl.util :as ueta]
    [integrant.core :as ig])
  (:import (java.io File)))

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
      (eta/set-window-size driver 1920 1080)
      (eta/screenshot driver filename)
      (println "saved screenshot"))
    (catch Throwable e
      (println "failed to save screenshot")
      (.printStackTrace e))))

(defn export-browser-coverage! [driver ^String out-path]
  (try
    (when-let [cov (try
                     (eta/js-execute driver "return (window.__coverage__ || null);")
                     (catch Throwable _ nil))]
      (let [s (json/generate-string cov)
            f (File. out-path)
            parent (.getParentFile f)]
        (when parent
          (.mkdirs parent))
        (spit f s)
        (println "Wrote browser coverage to" (.getAbsolutePath f))))
    (catch Throwable e
      (println "Failed to export browser coverage:" (.getMessage e)))))

(defn ->driver []
  (let [headless? (= "true" (System/getenv "HEADLESS"))]
    (eta/chrome {:headless    headless?
                 :path-driver (or (System/getenv "CHROMEDRIVER_PATH")
                                  "chromedriver")
                 :args        (when headless?
                                ["--no-sandbox"
                                 "--disable-dev-shm-usage"])})))

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
               (try
                 (when-let [out-path# (some-> (System/getenv "BROWSER_COV_DIR")
                                              (str "/browser-coverage.json"))]
                   (export-browser-coverage! ~driver-binding out-path#))
                 (catch Throwable _#
                   (println "browser coverage export failed")))
               (eta/quit ~driver-binding))))))))
