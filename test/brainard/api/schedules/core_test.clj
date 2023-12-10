(ns brainard.api.schedules.core-test
  (:require
    [brainard.api.notes.interfaces :as inotes]
    [brainard.api.schedules.core :as sched]
    [brainard.api.schedules.interfaces :as isched]
    [clojure.test :refer [deftest is testing]]))

(deftest relevant-notes-test
  (testing "when querying relevant notes"
    (let [query (promise)
          timestamp #inst "2024-04-17T07:11:31.715Z"
          mock (reify
                 isched/ISchedulesStore
                 (get-schedules [_ filters]
                   (deliver query filters)
                   [1 2 3])

                 inotes/INotesStore
                 (get-notes-by-ids [_ ids]
                   (map (partial hash-map :notes/id) ids)))
          results (sched/relevant-notes {:store mock :notes-api {:store mock}} timestamp)]
      (testing "sends a query to the schedule store"
        (is (= {:schedules/after-timestamp  timestamp
                :schedules/before-timestamp timestamp
                :schedules/day              17
                :schedules/month            :april
                :schedules/weekday          :wednesday
                :schedules/week-index       2}
               @query)))

      (testing "returns the results from the notes store"
        (is (= [{:notes/id   1
                 :notes/tags #{}}
                {:notes/id   2
                 :notes/tags #{}}
                {:notes/id   3
                 :notes/tags #{}}]
               results))))))