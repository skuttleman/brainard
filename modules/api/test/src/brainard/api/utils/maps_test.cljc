(ns brainard.api.utils.maps-test
  (:require
    [brainard.api.utils.maps :as maps]
    [clojure.test :refer [deftest is testing]]))

(deftest assoc-defaults-test
  (testing "adds missing keys"
    (is (= {:a 1 :b 2} (maps/assoc-defaults {:a 1} :b 2))))

  (testing "does not overwrite existing keys"
    (is (= {:a 1} (maps/assoc-defaults {:a 1} :a 99))))

  (testing "overwrites keys with nil values"
    (is (= {:a 99} (maps/assoc-defaults {:a nil} :a 99))))

  (testing "adds multiple missing keys"
    (is (= {:a 1 :b 2 :c 3} (maps/assoc-defaults {} :a 1 :b 2 :c 3))))

  (testing "works with nil map"
    (is (= {:a 1} (maps/assoc-defaults nil :a 1)))))

(deftest update-when-test
  (testing "applies update when key exists"
    (is (= {:a 2} (maps/update-when {:a 1} :a inc))))

  (testing "does not apply update when value is false"
    (is (= {:a false} (maps/update-when {:a false} :a not))))

  (testing "passes extra args to the function"
    (is (= {:a 3} (maps/update-when {:a 1} :a + 2)))))

(deftest deep-merge-test
  (testing "merges flat maps"
    (is (= {:a 1 :b 2} (maps/deep-merge {:a 1} {:b 2}))))

  (testing "second map overwrites scalar values"
    (is (= {:a 2} (maps/deep-merge {:a 1} {:a 2}))))

  (testing "deeply merges nested maps"
    (is (= {:a {:b 1 :c 2}} (maps/deep-merge {:a {:b 1}} {:a {:c 2}}))))

  (testing "nested scalar is overwritten by nested scalar"
    (is (= {:a {:b 2}} (maps/deep-merge {:a {:b 1}} {:a {:b 2}}))))

  (testing "non-map value in second overwrites nested map in first"
    (is (= {:a 99} (maps/deep-merge {:a {:b 1}} {:a 99}))))

  (testing "with nil first map"
    (is (= {:a 1} (maps/deep-merge nil {:a 1}))))

  (testing "with nil second map"
    (is (= nil (maps/deep-merge {:a 1} nil)))))
