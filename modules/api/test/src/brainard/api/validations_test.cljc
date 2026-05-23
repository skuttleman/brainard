(ns brainard.api.validations-test
  (:require
    [brainard.api.validations :as val]
    [clojure.test :refer [deftest is testing]]))

(def ^:private name-spec
  [:map
   [:first-name string?]
   [:last-name string?]])

(deftest ->validator-test
  (testing "returns nil for valid data"
    (is (nil? ((val/->validator name-spec) {:first-name "John" :last-name "Doe"}))))

  (testing "returns humanized errors for invalid data"
    (let [errors ((val/->validator name-spec) {:first-name 3})]
      (is (map? errors))
      (is (contains? errors :first-name))
      (is (contains? errors :last-name))))

  (testing "returns nil for valid data with optional keys absent"
    (is (nil? ((val/->validator [:map [:a {:optional true} string?]]) {})))))

(deftest validate!-test
  (testing "does not throw for valid data"
    (is (nil? (val/validate! name-spec {:first-name "Jane" :last-name "Doe"} ::test))))

  (testing "throws ex-info with ::type for invalid data"
    (let [ex (try
               (val/validate! name-spec {:first-name 3} ::test)
               nil
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) e e))]
      (is (some? ex))
      (is (= ::test (-> ex ex-data :brainard.api.validations/type)))
      (is (contains? (ex-data ex) :details))
      (is (contains? (ex-data ex) :data)))))

(deftest select-spec-keys-test
  (testing ":map - keeps only spec-defined keys"
    (is (= {:a 1 :b 2}
           (val/select-spec-keys {:a 1 :b 2 :extra "ignored"}
                                 [:map [:a int?] [:b {:optional true} int?]]))))

  (testing ":map - absent optional keys are not added"
    (is (= {:a 1}
           (val/select-spec-keys {:a 1}
                                 [:map [:a int?] [:b {:optional true} int?]]))))

  (testing ":map - recursively applies to nested map values"
    (is (= {:a {:x 1}}
           (val/select-spec-keys {:a {:x 1 :y 2}}
                                 [:map [:a [:map [:x int?]]]]))))

  (testing ":map-of - recursively applies spec to each value"
    (is (= {:k1 {:x 1} :k2 {:x 2}}
           (val/select-spec-keys {:k1 {:x 1 :y "drop"} :k2 {:x 2 :z "drop"}}
                                 [:map-of keyword? [:map [:x int?]]]))))

  (testing ":set - recursively applies spec to each element"
    (is (= #{{:x 1} {:x 2}}
           (val/select-spec-keys #{{:x 1 :y "drop"} {:x 2 :z "drop"}}
                                 [:set [:map [:x int?]]]))))

  (testing ":sequential - recursively applies spec to each element"
    (is (= [{:x 1} {:x 2}]
           (val/select-spec-keys [{:x 1 :y "drop"} {:x 2 :z "drop"}]
                                 [:sequential [:map [:x int?]]]))))

  (testing ":seqable - recursively applies spec to each element"
    (is (= [{:x 1}]
           (val/select-spec-keys [{:x 1 :y "drop"}]
                                 [:seqable [:map [:x int?]]]))))

  (testing ":and - delegates to the second element"
    (is (= {:a 1}
           (val/select-spec-keys {:a 1 :extra "drop"}
                                 [:and [:map [:a int?]] [:fn map?]]))))

  (testing "non-seqable spec - returns m unchanged"
    (is (= "hello"
           (val/select-spec-keys "hello" string?)))))
