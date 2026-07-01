(ns brainard.test.harness.ui.system
  (:require
   [brainard :as-alias b]
   [brainard.env :as env]
   [brainard.infra.db.store :as ds]
   [brainard.infra.search.lucene :as lucene]
   [brainard.test.harness.integration.system :as tsys]
   [brainard.test.harness.ui.web :as web]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :as t]
   [etaoin.api :as eta]
   [etaoin.impl.util :as ueta]
   [integrant.core :as ig]))

(defmethod ig/init-key :cfg.test/server-port
  [_ _]
  (ueta/get-free-port))

(defn ^:private save! [data db index]
  (ds/transact! db data)
  (let [seen? (volatile! {})]
    (doseq [{:notes/keys [id body context]} data
            :let [note-id (str id)
                  doc (cond-> {:id note-id}
                        body (assoc :body body)
                        context (assoc :context context))]
            :when id]
      (if-let [doc' (@seen? id)]
        (lucene/replace! index note-id (merge doc' doc))
        (lucene/add! index doc))
      (vswap! seen? update id merge doc))))

(defn transact-multi! [db index data]
  (if (map? data)
    (->> data
         (sort-by key)
         (into [] (mapcat (fn [[_ tx-data]]
                            (doto tx-data
                              (save! db index))))))
    (doto data
      (save! db index))))

(defn safe-screenshot! [driver]
  (try
    (let [filename (-> t/*testing-vars*
                       first
                       str
                       (string/replace #"/" "__")
                       (string/replace #"^#'" "")
                       gensym)]
      (eta/screenshot driver (str filename ".png"))
      (when-let [logs (seq (eta/get-logs driver))]
        (spit (str filename ".log") (string/join "\n" logs))))
    (catch Throwable e
      (.printStackTrace e))))

(defn collect-js-coverage! [driver]
  (when (env/get "JS_COVERAGE" 'Bool)
    (try
      (let [coverage-json (eta/js-execute driver "return JSON.stringify(window.__coverage__ || null)")
            nyc-output-dir (io/file "target/nyc_output")]
        (when (and coverage-json (not= "null" coverage-json))
          (.mkdirs nyc-output-dir)
          (spit (io/file nyc-output-dir (str (random-uuid) ".json"))
                coverage-json)))
      (catch Throwable _))))

(defn ->driver []
  (let [headless? (env/get "HEADLESS" 'Bool)]
    (eta/chrome {:headless     headless?
                 :capabilities {:pageLoadStrategy "eager"}
                 :path-driver  (env/get "CHROMEDRIVER_PATH" "chromedriver")
                 :args         (into ["--window-size=1200,900"]
                                     (when headless?
                                       ["--no-sandbox"
                                        "--disable-dev-shm-usage"]))})))

(defmacro with-webdriver [[driver-binding base-url-binding seeds&opts] & body]
  (let [port-sym (gensym "port")
        db-sym (gensym "db")
        idx-sym (gensym "idx")
        event-sym (gensym "event-ch")
        sys-bindings (into {port-sym  :cfg.test/server-port
                            db-sym    ::b/IDBConn
                            idx-sym   ::b/IIndex
                            event-sym ::b/event-ch}
                           (filter (fn [[k]] (and (keyword? k) (= "keys" (name k)))))
                           seeds&opts)]
    `(let [opts# ~(select-keys seeds&opts #{:init-keys :env})]
       (env/with-env (:env opts#)
         (tsys/with-app [~sys-bindings
                         {:config    "duct/ui-test.edn"
                          :init-keys (:init-keys opts# [::b/webserver])
                          :timeout   30000}]
           (let [screenshot?# (env/get "SCREENSHOT" (symbol "Bool"))
                 disable-sse?# (env/get "DISABLE_SSE" (symbol "Bool") true)
                 orig-report# t/report
                 ~driver-binding (try (->driver)
                                      (catch Throwable _#
                                        (->driver)))
                 ~base-url-binding (str "http://localhost:" ~port-sym)
                 ~@(for [[sym seed] seeds&opts
                         :when (symbol? sym)
                         token [sym (cond->> `(->> ~seed
                                                   (str "seed/")
                                                   web/edn-fixture
                                                   (transact-multi! ~db-sym ~idx-sym))
                                      (:defer (meta sym)) (list `fn []))]]
                     token)]
             (binding [t/report (fn [event#]
                                  (when (and (#{:fail :error} (:type event#))
                                             screenshot?#)
                                    (safe-screenshot! ~driver-binding))
                                  (orig-report# event#))]
               (try
                 (when disable-sse?#
                   (async/go-loop []
                     (when-let [val# (async/<! ~event-sym)]
                       (web/event ~driver-binding val#)
                       (recur))))
                 ~@body
                 (catch Throwable e#
                   (when screenshot?#
                     (safe-screenshot! ~driver-binding))
                   (throw e#))
                 (finally
                   (collect-js-coverage! ~driver-binding)
                   (eta/quit ~driver-binding))))))))))
