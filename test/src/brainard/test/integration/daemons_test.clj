(ns brainard.test.integration.daemons-test
  (:require
    [brainard :as-alias b]
    [brainard.api.events.interfaces :as ievents]
    [brainard.api.storage.core :as storage]
    [brainard.attachments.api.core :as api.attachments]
    [brainard.infra.system.daemons :as daemons]
    [brainard.test.harness.integration.system :as tsys]
    [cljc.java-time.instant :as inst]
    [cljc.java-time.zone-id :as zi]
    [cljc.java-time.zoned-date-time :as zdt]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]
    [slag.utils.uuids :as uuids])
  (:import
    (java.util Date)))

(deftest cleanup-orphaned-artifacts!-test
  (tsys/with-app [{::b/keys [obj-storage storage]} nil]
    (let [attachment-id (random-uuid)
          stream (io/file (io/resource "fixtures/sample.txt"))]
      (testing "when saving an orphaned artifact"
        (storage/execute! obj-storage {::storage/type            ::api.attachments/upload!
                                       :attachments/id           attachment-id
                                       :attachments/content-type "plain/text"
                                       :attachments/stream       stream})

        (testing "can download the artifact"
          (is (storage/query obj-storage {::storage/type  ::api.attachments/download
                                          :attachments/id attachment-id})))

        (testing "and when cleaning up orphaned artifacts"
          (daemons/cleanup-orphaned-artifacts! storage obj-storage)

          (testing "cannot download the artifact"
            (is (nil? (storage/query obj-storage {::storage/type  ::api.attachments/download
                                                  :attachments/id attachment-id}))))

          (testing "and when there are no artifacts"
            (testing "returns nil"
              (is (nil? (daemons/cleanup-orphaned-artifacts! storage obj-storage)))))

          (testing "and when the cleanup fails"
            (testing "returns nil"
              (is (nil? (daemons/cleanup-orphaned-artifacts! nil nil))))))))))

(deftest update-buzz!-test
  (tsys/with-app [{::b/keys [apis storage]} nil]
    (let [msgs (atom [])
          mock-events (reify ievents/ISend
                        (broadcast! [_ type data]
                          (swap! msgs conj [type data])))
          [n1 n2 s1] (repeatedly uuids/random)
          timestamp (Date.)
          weekday (-> timestamp
                      .getTime
                      inst/of-epoch-milli
                      (zdt/of-instant (zi/of "UTC"))
                      zdt/get-day-of-week
                      str
                      string/lower-case
                      keyword)]
      (testing "when creating notes"
        (storage/execute! storage
                          [{:notes/id      n1
                            :notes/context "Context"
                            :notes/body    "body of note"
                            :notes/tags    #{:tag}
                            :notes/pinned? true}
                           {:notes/id      n2
                            :notes/context "Another Context"
                            :notes/body    "body of another note"
                            :notes/pinned? false}])

        (testing "and when adding a schedule"
          (storage/execute! storage
                            [{:schedules/id      s1
                              :schedules/note-id n1
                              :schedules/weekday weekday}])

          (testing "and when updating buzz"
            (daemons/update-buzz! apis mock-events timestamp)

            (testing "broadcasts the relevant note"
              (let [notes (->> @msgs
                               (mapcat (comp :data second second))
                               (map #(dissoc % :notes/timestamp)))]
                (is (= 1 (count @msgs)))
                (is (= :message (ffirst @msgs)))
                (is (= :notes/relevant (-> @msgs first second first)))
                (is (= [{:notes/id          n1
                         :notes/context     "Context"
                         :notes/body        "body of note"
                         :notes/tags        #{:tag}
                         :notes/pinned?     true
                         :notes/attachments #{}
                         :notes/todos       #{}}]
                       notes))))))))))
