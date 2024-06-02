(ns brainard.api.schedules-test
  (:require
    [brainard.api.core :as api]
    [brainard.schedules.api.core :as-alias api.sched]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.storage.core :as storage]
    [clojure.test :refer [deftest is testing]]))

(defmethod istorage/->input :default
  [params]
  params)


(deftest relevant-notes-test
  (testing "when querying relevant notes"
    (let [timestamp #inst "2024-04-17T07:11:31.715Z"
          mock (reify
                 istorage/IRead
                 (read [_ filters]
                   (if (= ::api.sched/schedules (::storage/type filters))
                     [{:schedules/note-id 1}
                      {:schedules/note-id 2}
                      {:schedules/note-id 3}]
                     [{:notes/id 1}
                      {:notes/id 2
                       :notes/tags #{:foo}}
                      {:notes/id 3}])))
          results (api/relevant-notes {:schedules {:store mock}
                                       :notes     {:store mock}}
                                      timestamp)]

      (testing "returns the results from the notes store"
        (is (= [{:notes/id   1
                 :notes/tags #{}}
                {:notes/id   2
                 :notes/tags #{:foo}}
                {:notes/id   3
                 :notes/tags #{}}]
               results))))))
