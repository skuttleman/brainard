(ns brainard.infra.db.schedules-test
  (:require
    [brainard.common.api.schedules.interfaces :as isched]
    [brainard.common.utils.uuids :as uuids]
    [brainard.test.system :as tsys]
    [clojure.test :refer [deftest is testing]]
    brainard.infra.system))

(deftest get-schedules-test
  (tsys/with-system [{:brainard/keys [schedules-store]} nil]
    (testing "when saving a schedule"
      (let [[s1 s2 s3 s4 s5 s6 s7] (repeatedly uuids/random)
            timestamp #inst "2023-12-09T20:31:04.197Z"
            filters {:schedules/after-timestamp  timestamp
                     :schedules/before-timestamp timestamp
                     :schedules/day              9
                     :schedules/month            :december
                     :schedules/week-index       1
                     :schedules/weekday          :saturday}]
        (run! (partial isched/save! schedules-store)
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
          (let [results (isched/get-schedules schedules-store filters)]
            (testing "matches all schedules"
              (is (= #{s1 s2 s3 s4 s5 s6 s7}
                     (into #{} (map :schedules/id) results)))))

          (testing "and when updating schedules with non-matching characteristics"
            (run! (partial isched/save! schedules-store)
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
              (let [results (isched/get-schedules schedules-store filters)]
                (testing "matches all schedules"
                  (is (= #{s7} (into #{} (map :schedules/id) results))))))))))))
