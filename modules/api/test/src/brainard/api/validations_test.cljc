(ns brainard.api.validations-test
  (:require
    [brainard.api.validations :as val]
    [clojure.test :refer [deftest is testing]])
  #?(:clj
     (:import
       (clojure.lang ExceptionInfo))))

(def ^:private name-spec
  [:map
   [:first-name string?]
   [:last-name string?]])

(deftest ->validator-test
  (testing "when the data is valid"
    (testing "returns nil"
      (is (nil? ((val/->validator name-spec) {:first-name "John" :last-name "Doe"})))))

  (testing "when the data is invalid"
    (testing "returns humanized errors"
      (let [errors ((val/->validator name-spec) {:first-name 3})]
        (is (map? errors))
        (is (contains? errors :first-name))
        (is (contains? errors :last-name)))))

  (testing "returns nil for valid data with optional keys absent"
    (is (nil? ((val/->validator [:map [:a {:optional true} string?]]) {})))))

(deftest validate!-test
  (testing "when the data is valid"
    (testing "returns nil"
      (is (nil? (val/validate! name-spec {:first-name "Jane" :last-name "Doe"} ::test)))))

  (testing "when the data is invalid"
    (testing "throws"
      (let [ex (is (thrown? #?(:clj ExceptionInfo :cljs js/Error)
                            (val/validate! name-spec {:first-name 3} ::test)))]
        (is (= ::test (-> ex ex-data :brainard.api.validations/type)))
        (is (contains? (ex-data ex) :details))
        (is (contains? (ex-data ex) :data))))))

(deftest select-spec-keys-test
  (testing "when the spec is :map"
    (testing "keeps only spec-defined keys"
      (is (= {:a 1 :b 2}
             (val/select-spec-keys {:a 1 :b 2 :extra "ignored"}
                                   [:map [:a int?] [:b {:optional true} int?]]))))

    (testing "does not add optional keys"
      (is (= {:a 1}
             (val/select-spec-keys {:a 1}
                                   [:map [:a int?] [:b {:optional true} int?]]))))

    (testing "applies recursively to nested map values"
      (is (= {:a {:x 1}}
             (val/select-spec-keys {:a {:x 1 :y 2}}
                                   [:map [:a [:map [:x int?]]]])))))

  (testing "when the spec is :map-of"
    (testing "applies recursively to each value"
      (is (= {:k1 {{} "keep"} :k2 {{:x 2} [3]}}
             (val/select-spec-keys {:k1 {{"x" 1} "keep"} :k2 {{:x 2 "y" 3} [3]}}
                                   [:map-of keyword? [:map-of [:map [:x keyword?]] int?]])))))

  (testing "when the spec is :set"
    (testing "applies recursively to each element"
      (is (= #{{:x #{1}} {:x #{2}}}
             (val/select-spec-keys #{{:x #{1} :y #{"drop"}} {:x #{2} :z #{3}}}
                                   [:set [:map [:x [:set int?]]]])))))

  (testing "when the spec is :sequential"
    (testing "applies recursively to each element"
      (is (= [{:x [1 2]} {:x [3]}]
             (val/select-spec-keys [{:x [1 2] :y ["drop"]} {:x [3] :z [4]}]
                                   [:sequential [:map [:x [:sequential int?]]]])))))

  (testing "when the spec is :seqable"
    (testing "applies recursively to each element"
      (is (= [{:x [1 2]}]
             (val/select-spec-keys [{:x [1 2] :y 3}]
                                   [:seqable [:map [:x [:seqable int?]]]])))))

  (testing "when the spec is :and"
    (testing "delegates to the second element"
      (is (= {:a 1}
             (val/select-spec-keys {:a 1 :extra "drop"}
                                   [:and [:map [:a int?]] [:fn map?]])))))

  (testing "when the spec is not seqable"
    (testing "returns unchanged value"
      (is (= "hello" (val/select-spec-keys "hello" string?))))))
