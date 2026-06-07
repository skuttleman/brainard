(ns brainard.test.runner
  (:require
    [brainard.api.utils.logger :as log]
    [clojure.java.io :as io]
    [etaoin.api :as eta]
    [hiccup2.core :as hiccup]
    [immutant.web :as web]
    [integrant.core :as ig]
    brainard.test.harness.ui.system))

(defn html [cljs]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:title "Brainard ClojureScript Tests"]
    [:style "body {
              font-family: monospace;
              padding: 20px;
              background-color: #f5f5f5;
          }
          #test-output {
              background-color: white;
              border: 1px solid #ddd;
              padding: 15px;
              border-radius: 4px;
              white-space: pre-wrap;
              word-wrap: break-word;
              min-height: 200px;
          }
          .pass {
              color: green;
          }
          .fail {
              color: red;
          }"]]
   [:body
    [:h1 "Brainard ClojureScript Tests"]
    [:div#test-output "Running tests..."]
    [:script {:src cljs}]]])

(defn ^:private handler [request resource cljs]
  (let [uri (:uri request)]
    (cond
      (= uri resource)
      {:status  200
       :headers {"Content-Type" "application/javascript"}
       :body    (io/file cljs)}

      (= uri "/")
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (->> resource
                     html
                     hiccup/html
                     (str "<!doctype html>"))}

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

(defn ^:private print-results! [server driver]
  (let [results (eta/js-execute driver "return window.testResults")]
    (eta/quit driver)
    (web/stop server)
    (if (and results
             (zero? (:failures results 0))
             (zero? (:errors results 0)))
      (do
        (println (log/green "✓ All tests passed!"))
        (System/exit 0))
      (do
        (println (log/red "✗ Tests failed or no results!"))
        (System/exit 1)))))

(defn ^:private run-on-rnd-port [resource cljs]
  (let [port (ig/init-key :cfg.test/server-port {})]
    [port (web/run #(handler % resource cljs) {:port port :host "localhost"})]))

(defn -main []
  (let [[port server] (try
                        (run-on-rnd-port "/cljs-test/test.js" "target/cljs-test/test.js")
                        (catch Throwable _
                          (run-on-rnd-port "/cljs-test/test.js" "target/cljs-test/test.js")))
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
      (print-results! server driver)
      (catch Throwable ex
        (println (str "\nError retrieving test results: " (.getMessage ex)))
        (.printStackTrace ex)
        (eta/quit driver)
        (web/stop server)
        (System/exit 1)))))
