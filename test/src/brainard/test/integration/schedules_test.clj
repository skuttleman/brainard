(ns brainard.test.integration.schedules-test
  (:require
   [brainard :as-alias b]
   [brainard.schedules.api.core :as api.sched]
   [brainard.api.storage.core :as storage]
   [brainard.schedules.api.relevancy :as relevancy]
   [brainard.test.harness.integration.system :as tsys]
   [cljc.java-time.instant :as inst]
   [clojure.test :refer [deftest is testing]]
   [slag.utils.uuids :as uuids])
  (:import
   (java.util Date)))

(deftest get-schedules-test
  (tsys/with-app [{::b/keys [storage]} nil]
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

(deftest delete-for-note!-test
  (tsys/with-app [{::b/keys [schedules-api]} nil]
    (let [[note-id-1 note-id-2] (repeatedly uuids/random)]
      (api.sched/create! schedules-api {:schedules/note-id note-id-1 :schedules/weekday :monday})
      (api.sched/create! schedules-api {:schedules/note-id note-id-1 :schedules/weekday :friday})
      (api.sched/create! schedules-api {:schedules/note-id note-id-2 :schedules/weekday :tuesday})
      (testing "when deleting schedules for a note"
        (api.sched/delete-for-note! schedules-api note-id-1)
        (testing "removes all schedules for that note"
          (is (empty? (api.sched/get-by-note-id schedules-api note-id-1))))
        (testing "does not remove schedules for other notes"
          (is (= 1 (count (api.sched/get-by-note-id schedules-api note-id-2)))))))))

(deftest relevant-schedules-test
  (tsys/with-app [{::b/keys [schedules-api storage]} nil]
    (let [[s1 s2 s3 s4 s5 s6 s7 s8 s9 s10 s11] (repeatedly uuids/random)
          now (Date.)
          later (-> now .toInstant (inst/plus-seconds 100) Date/from)
          earlier (-> now .toInstant (inst/minus-seconds 100) Date/from)
          {:keys [weekday month day week-index]} (relevancy/from now)
          other-weekday (if (= :sunday weekday) :monday :sunday)
          other-month (if (= :january month) :february :january)
          other-day (if (= 1 day) 2 1)
          other-week (if (= 1 week-index) 2 1)]
      (testing "when saving schedules"
        (storage/execute! storage
                          [{:schedules/id    s1
                            :schedules/month month}
                           {:schedules/id  s2
                            :schedules/day day}
                           {:schedules/id         s3
                            :schedules/week-index week-index}
                           {:schedules/id               s4
                            :schedules/after-timestamp  earlier
                            :schedules/before-timestamp later}
                           {:schedules/id      s5
                            :schedules/weekday weekday}
                           ;; other schedules
                           {:schedules/id    s6
                            :schedules/month other-month}
                           {:schedules/id  s7
                            :schedules/day other-day}
                           {:schedules/id         s8
                            :schedules/week-index other-week}
                           {:schedules/id              s9
                            :schedules/after-timestamp later}
                           {:schedules/id               s10
                            :schedules/before-timestamp earlier}
                           {:schedules/id      s11
                            :schedules/weekday other-weekday}])

        (testing "and when retrieving relevant schedules"
          (let [actual (api.sched/relevant-schedules schedules-api now)]
            (testing "returns expected schedules"
              (is (= #{s1 s2 s3 s4 s5}
                     (into #{} (map :schedules/id) actual))))))))))
