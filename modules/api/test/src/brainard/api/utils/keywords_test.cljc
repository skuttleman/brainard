(ns brainard.api.utils.keywords-test
  (:require
    [brainard.api.utils.keywords :as kw]
    [clojure.test :refer [deftest is testing]]))

(deftest str-test
  (testing "when passing a namespaced keyword"
    (testing "returns the stringified keyword"
      (is (= "foo/bar" (kw/str :foo/bar)))))

  (testing "when passing a name-only keyword"
    (testing "returns the stringified keyword"
      (is (= "baz" (kw/str :baz)))))

  (testing "when passing nil"
    (testing "returns nil"
      (is (nil? (kw/str nil))))))
