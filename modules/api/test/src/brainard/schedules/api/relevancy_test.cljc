(ns brainard.schedules.api.relevancy-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [brainard.schedules.api.relevancy :as relevancy]))

(deftest from-test
  (testing "parses dates"
    (are [timestamp expected] (= expected (relevancy/from timestamp))
      #inst "2021-04-13T17:33:51.919Z" {:day        13
                                        :month      :april
                                        :week-index 1
                                        :weekday    :tuesday}
      #inst "1970-12-25Z" {:day        25
                           :month      :december
                           :week-index 3
                           :weekday    :friday})))
