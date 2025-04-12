(ns brainard.test.integration.schedules-test
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.uuids :as uuids]
    [brainard.schedules.api.core :as api.sched]
    [brainard.api.storage.core :as storage]
    [brainard.test.system :as tsys]
    [clojure.test :refer [deftest is testing]]))

(deftest get-schedules-test
  (tsys/with-system [{::b/keys [storage]} nil]
    (testing "when saving a schedule"
      (let [[s1 s2 s3 s4 s5 s6 s7] (repeatedly uuids/random)
            timestamp #inst "2023-12-09T20:31:04.197Z"
            filters {::storage/type              ::api.sched/schedules
                     :schedules/after-timestamp  timestamp
                     :schedules/before-timestamp timestamp
                     :schedules/day              9
                     :schedules/month            :december
                     :schedules/week-index       1
                     :schedules/weekday          :saturday}]
        (storage/execute! storage
                          [{:schedules/id      s1
                            :schedules/weekday :saturday}
                           {:schedules/id    s2
                            :schedules/month :december}
                           {:schedules/id  s3
                            :schedules/day 9}
                           {:schedules/id         s4
                            :schedules/week-index 1}
                           {:schedules/id              s5
                            :schedules/after-timestamp timestamp}
                           {:schedules/id               s6
                            :schedules/before-timestamp timestamp}
                           {:schedules/id               s7
                            :schedules/weekday          :saturday
                            :schedules/month            :december
                            :schedules/day              9
                            :schedules/week-index       1
                            :schedules/after-timestamp  timestamp
                            :schedules/before-timestamp timestamp}])
        (testing "and when getting schedules"
          (let [results (storage/query storage filters)]
            (testing "matches all schedules"
              (is (= #{s1 s2 s3 s4 s5 s6 s7}
                     (into #{} (map :schedules/id) results)))))

          (testing "and when updating schedules with non-matching characteristics"
            (storage/execute! storage
                              [{:schedules/id    s1
                                :schedules/month :january}
                               {:schedules/id  s2
                                :schedules/day 12}
                               {:schedules/id         s3
                                :schedules/week-index 0}
                               {:schedules/id              s4
                                :schedules/after-timestamp #inst "2025-01-01T00:00:00Z"}
                               {:schedules/id               s5
                                :schedules/before-timestamp #inst "1901-01-01T00:00:00Z"}
                               {:schedules/id      s6
                                :schedules/weekday :wednesday}])
            (testing "only matches the relevant schedule"
              (let [results (storage/query storage filters)]
                (testing "matches all schedules"
                  (is (= #{s7} (into #{} (map :schedules/id) results))))))))))))
