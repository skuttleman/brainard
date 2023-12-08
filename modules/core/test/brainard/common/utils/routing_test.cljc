(ns brainard.common.utils.routing-test
  (:require
    [brainard.common.utils.routing :as rte]
    [clojure.test :refer [deftest is testing]]))

(deftest ->query-string-test
  (testing "generates a query-string from params"
    (is (= "a=foo&b=2&b=3&c&d=" (rte/->query-string (array-map :a :foo :na/b [2 "3"] :c true :d "" :e nil)))))

  (testing "returns nil when there are no params"
    (is (nil? (rte/->query-string {})))
    (is (nil? (rte/->query-string nil)))))

(deftest ->query-params-test
  (testing "parses a query-string into params"
    (is (= {:a "1" :b #{"2" "3"} :c true :d ""} (rte/->query-params "a=1&b=2&b=3&c&d="))))

  (testing "returns nil when there is no query-string"
    (is (nil? (rte/->query-params "")))
    (is (nil? (rte/->query-params nil)))))
