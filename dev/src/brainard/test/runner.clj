(ns brainard.test.runner
  (:require
    [brainard.api.utils.logger :as log]
    [clojure.java.io :as io]
    [etaoin.api :as eta]
    [immutant.web :as web]
    [integrant.core :as ig]
    brainard.test.harness.ui.system))

(defn ^:private handler [request]
  (let [uri (:uri request)]
    (cond
      (= uri "/cljs-test/test.js")
      {:status  200
       :headers {"Content-Type" "application/javascript"}
       :body    (io/file "target/cljs-test/test.js")}

      (= uri "/")
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (io/file "test/resources/html/test-runner.html")}

      :else
      {:status 404
       :body   "Not Found"})))

(defn ^:private print-logs! [driver]
  (when-let [logs (seq (eta/get-logs driver))]
    (println "\nBrowser Console:")
    (doseq [{:keys [level message]} logs
            :let [[_ msg] (re-find #"\"(.+)\"" message)]
            :let [msg (cond-> (str msg)
                        (= :severe level) log/red
                        (= :warning level) log/yellow)]]
      (println msg))))

(defn ^:private print-results! [driver]
  (let [results (eta/js-execute driver "return window.testResults")]
    (if (and results
             (zero? (:failures results 0))
             (zero? (:errors results 0)))
      (do
        (println (log/green "✓ All tests passed!"))
        (System/exit 0))
      (do
        (println (log/red "✗ Tests failed or no results!"))
        (System/exit 1)))))

(defn ^:private run-on-rnd-port []
  (let [port (ig/init-key :cfg.test/server-port {})]
    [port (web/run handler {:port port :host "localhost"})]))

(defn -main []
  (let [[port server] (try
                        (run-on-rnd-port)
                        (catch Throwable _
                          (run-on-rnd-port)))
        driver (try
                 (eta/chrome {:headless true})
                 (catch Throwable _
                   (eta/chrome {:headless true})))
        url (str "http://localhost:" port)]
    (try
      (println (str "Starting test server on port " port))
      (println (str "Opening test runner: " url))
      (eta/go driver url)
      (eta/wait-visible driver {:css ".complete"})

      (print-logs! driver)
      (print-results! driver)
      (catch Throwable ex
        (println (str "\nError retrieving test results: " (.getMessage ex)))
        (.printStackTrace ex)
        (System/exit 1))
      (finally
        (eta/quit driver)
        (web/stop server)))))
