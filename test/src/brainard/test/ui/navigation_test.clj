(ns brainard.test.ui.navigation-test
  (:require
    [brainard.test.ui-system :as ui-sys]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest home-page-test
  (ui-sys/with-system [driver base-url]
    (eta/go driver base-url)
    (testing "renders app title"
      (eta/wait-visible driver {:css "h1.title"})
      (is (= "brainard" (eta/get-element-text driver {:css "h1.title"}))))

    (testing "renders pinned notes section"
      (is (true? (eta/wait-visible driver {:xpath "//h1/strong[text()='Pinned notes']"}))))

    (testing "renders workspace section"
      (is (true? (eta/wait-visible driver {:xpath "//h1/strong[text()='Workspace']"}))))))

(deftest search-page-test
  (ui-sys/with-system [driver base-url]
    (eta/go driver (str base-url "/search"))
    (testing "renders search form"
      (is (true? (eta/wait-visible driver {:xpath "//button[text()='Search']"}))))

    (testing "search nav item is active"
      (is (true? (eta/wait-visible driver {:xpath "//li[contains(@class,'is-active')]//a[text()='Search']"}))))))

(deftest buzz-page-test
  (ui-sys/with-system [driver base-url]
    (eta/go driver (str base-url "/buzz"))
    (testing "renders app title"
      (is (true? (eta/wait-visible driver {:css "h1.title"}))))

    (testing "buzz nav item is active"
      (is (true? (eta/wait-visible driver {:xpath "//li[contains(@class,'is-active')]//a[text()='Buzz']"}))))))
