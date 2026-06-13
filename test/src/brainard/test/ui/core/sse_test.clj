(ns brainard.test.ui.core.sse-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.test :refer [deftest is testing]]
   [duct.core.env :as duct.env]
   [etaoin.api :as eta]))

(deftest event-stream-test
  (binding [duct.env/*env* (merge duct.env/*env* {"BUZZ_INTERVAL" "5000"
                                                  "DISABLE_SSE"   "false"})]
    (usys/with-webdriver [driver base-url {:init-keys    [:brainard/webserver :brainard/buzzer]
                                           ^:defer load! "buzz.edn"}]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "does not display a badge on the buzz tab"
          (is (not (eta/exists? driver {:css ".navbar .navbar__buzz .tag.is-rounded"}))))

        (testing "and when creating a note with a matching schedule"
          (load!)

          (testing "and when receiving the buzz event"
            (eta/wait-exists driver {:css ".navbar .navbar__buzz .tag.is-rounded"} {:timeout 10})

            (testing "displays a badge on the buzz tab"
              (is (eta/has-text? driver {:css ".navbar .navbar__buzz .tag.is-rounded"} "1")))))))))
