(ns brainard.test.runner
  (:require
    [clojure.java.io :as io]
    [etaoin.api :as eta]
    [immutant.web :as web]
    [integrant.core :as ig]
    brainard.test.harness.ui.system))

(defn handler [request]
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

(def red "\u001b[31m")
(def green "\u001b[32m")
(def yellow "\u001b[33m")
(def reset "\u001b[0m")

(defn ^:private print-logs! [driver]
  (when-let [logs (seq (eta/get-logs driver))]
    (println "\nBrowser Console:")
    (doseq [{:keys [level message]} logs
            :let [[_ msg] (re-find #"\"(.+)\"" message)]
            :let [msg (cond-> (str msg)
                        (= :severe level) (as-> $ (str red $ reset))
                        (= :warning level) (as-> $ (str yellow $ reset)))]]
      (println msg))))

(defn ^:private print-results! [driver]
  (let [results (eta/js-execute driver "return window.testResults")]
    (if (and results
             (zero? (:failures results 0))
             (zero? (:errors results 0)))
      (do
        (println green "✓ All tests passed!" reset)
        (System/exit 0))
      (do
        (println red "✗ Tests failed or no results!" reset)
        (System/exit 1)))))

(defn -main []
  (let [port (ig/init-key :cfg.test/server-port {})
        url (str "http://localhost:" port)
        server (web/run handler {:port port :host "localhost"})
        driver (eta/chrome {:headless true})]
    (try
      (println (str "Starting test server on port " port))
      (Thread/sleep 500)

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
