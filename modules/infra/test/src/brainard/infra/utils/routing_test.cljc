(ns brainard.infra.utils.routing-test
  (:require
    [brainard.infra.utils.routing :as rte]
    [clojure.test :refer [deftest is testing]]))

(deftest ->query-string-test
  (testing "generates a query-string from params"
    (is (= "a=foo&b=2&b=3&c&d=" (rte/->query-string (array-map :a :foo :na/b [2 "3"] :c true :d "" :e nil)))))

  (testing "handles spaces"
    (is (= "foo=Some+Space" (rte/->query-string {:foo "Some Space"}))))

  (testing "returns nil when there are no params"
    (is (nil? (rte/->query-string {})))
    (is (nil? (rte/->query-string nil)))))

(deftest ->query-params-test
  (testing "parses a query-string into params"
    (is (= {:a "1" :b #{"2" "3"} :c true :d ""} (rte/->query-params "a=1&b=2&b=3&c&d="))))

  (testing "handles spaces"
    (is (= {:a "Some Thing" :b "Some Thing"} (rte/->query-params "a=Some+Thing&b=Some%20Thing"))))

  (testing "returns nil when there is no query-string"
    (is (nil? (rte/->query-params "")))
    (is (nil? (rte/->query-params nil)))))
