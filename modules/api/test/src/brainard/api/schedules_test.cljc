(ns brainard.api.schedules-test
  (:require
    [brainard.api.core :as api]
    [brainard.notes.api.interfaces :as inotes]
    [brainard.schedules.api.interfaces :as isched]
    [clojure.test :refer [deftest is testing]]))

(deftest relevant-notes-test
  (testing "when querying relevant notes"
    (let [query (promise)
          timestamp #inst "2024-04-17T07:11:31.715Z"
          mock (reify
                 isched/IReadSchedules
                 (get-schedules [_ filters]
                   (deliver query filters)
                   [{:schedules/note-id 1}
                    {:schedules/note-id 2}
                    {:schedules/note-id 3}])

                 inotes/IReadNotes
                 (get-notes [_ {:notes/keys [ids]}]
                   (map (partial hash-map :notes/id) ids)))
          results (api/relevant-notes {:schedules {:store mock} :notes {:store mock}} timestamp)]
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
