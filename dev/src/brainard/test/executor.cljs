(ns brainard.test.executor
  (:require
    [clojure.string :as string]
    [clojure.test :refer [run-tests]]
    brainard.api.utils.maps-test
    brainard.api.validations-test
    brainard.infra.store-test
    brainard.infra.store.events-test
    brainard.infra.store.queries-test
    brainard.infra.store.specs-test
    brainard.schedules.api.relevancy-test))

(defn ^:private parse-test-results [results]
  (when-let [[_ tests assertions failures errors]
             (re-find #"(\d+) tests containing (\d+) assertions\.\n(\d+) failures, (\d) errors"
                      (str results))]
    {:tests      (parse-long tests)
     :assertions (parse-long assertions)
     :failures   (parse-long failures)
     :errors     (parse-long errors)}))

(defn ^:private log-output! [output]
  (js/console.log "=== Test Results ===")
  (doseq [line (string/split-lines output)
          :let [error? (or (string/starts-with? line "FAIL in")
                           (string/starts-with? line "ERROR in"))
                warn? (or (string/starts-with? line "expected:")
                          (string/starts-with? line "  actual:"))]]
    (cond
      error? (js/console.error line)
      warn? (js/console.warn line)
      :else (js/console.log line))))

(defn ^:private update-page! [results]
  (when-let [output (js/document.getElementById "test-output")]
    (set! (.-innerHTML output)
          (str "<span class=\"complete\">Tests Finished Running</span>"
               (when-not (zero? (:tests results 0))
                 (str "<span class=\"pass\">✓ " (:tests results) " tests run</span><br>"))
               (when-not (zero? (:failures results 0))
                 (str "<span class=\"fail\">✗ " (:failures results) " failures</span><br>"))
               (when-not (zero? (:errors results 0))
                 (str "<span class=\"fail\">✗ " (:errors results) " errors</span><br>"))
               (if (and (zero? (:failures results 0))
                        (zero? (:errors results 0)))
                 "<span class=\"pass\">✓ All tests passed!</span>"
                 "<span class=\"fail\">✗ Some tests failed</span>")))))

(defn ^:export test! []
  (let [output (with-out-str (run-tests
                               'brainard.api.utils.maps-test
                               'brainard.api.validations-test
                               'brainard.infra.store-test
                               'brainard.infra.store.events-test
                               'brainard.infra.store.queries-test
                               'brainard.infra.store.specs-test
                               'brainard.schedules.api.relevancy-test))
        results (parse-test-results output)]
    (aset js/window "testResults" (clj->js results))
    (aset js/window "testsPassed" (and (pos? (:tests results 0))
                                       (zero? (+ (:failures results 0) (:errors results 0)))))
    (log-output! output)
    (update-page! results)))
