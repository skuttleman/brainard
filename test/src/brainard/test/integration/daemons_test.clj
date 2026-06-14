(ns brainard.test.integration.daemons-test
  (:require
   [brainard :as-alias b]
   [brainard.api.events.interfaces :as ievents]
   [brainard.api.storage.core :as storage]
   [brainard.api.storage.interfaces :as istorage]
   [brainard.attachments.api.core :as api.attachments]
   [brainard.infra.db.store :as ds]
   [brainard.infra.system.daemons :as daemons]
   [brainard.test.harness.integration.system :as tsys]
   [cljc.java-time.instant :as inst]
   [cljc.java-time.temporal.chrono-unit :as cu]
   [cljc.java-time.zone-id :as zi]
   [cljc.java-time.zoned-date-time :as zdt]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [slag.utils.edn :as edn]
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
                          [{:notes/id        n1
                            :notes/context   "Context"
                            :notes/body      "body of note"
                            :notes/pinned?   true
                            :notes/archived? false
                            :notes/tags      #{:tag}}
                           {:notes/id        n2
                            :notes/context   "Another Context"
                            :notes/body      "body of another note"
                            :notes/pinned?   false
                            :notes/archived? false}])

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
                         :notes/pinned?     true
                         :notes/archived?   false
                         :notes/tags        #{:tag}
                         :notes/attachments #{}
                         :notes/todos       #{}}]
                       notes))))))))))

(deftest delete-archived-notes!-test
  (let [store (ds/->DSStore (ds/connect! {:db-name     (str (uuids/random))
                                          :storage-dir :mem}))
        schema (edn/read (io/resource "db/schema.edn"))
        [n1 n2 n3 n4 s1 s2 s3 s4] (repeatedly uuids/random)
        old-inst (-> (inst/now)
                     (inst/minus 30 cu/days)
                     Date/from)]
    (testing "when there are old archived notes"
      (istorage/write! store (cons [:db/add "datomic.tx" :db/txInstant old-inst]
                                   schema))
      (istorage/write! store [[:db/add "datomic.tx" :db/txInstant old-inst]
                              {:notes/id        n1
                               :notes/archived? false}
                              {:notes/id        n2
                               :notes/archived? true}])
      (istorage/write! store [{:notes/id        n3
                               :notes/archived? false}
                              {:notes/id        n4
                               :notes/archived? true}
                              {:schedules/id      s1
                               :schedules/note-id n1}
                              {:schedules/id      s2
                               :schedules/note-id n2}
                              {:schedules/id      s3
                               :schedules/note-id n3}
                              {:schedules/id      s4
                               :schedules/note-id n4}])
      (testing "and when deleting old archived notes"
        (daemons/delete-archived-notes! store)

        (testing "deletes only old archived notes"
          (is (= #{n1 n3 n4}
                 (set (istorage/read store
                                     {:query '[:find ?id
                                               :where
                                               [?e :notes/id ?id]]
                                      :xform (map first)}))))

          (testing "and their schedules"
            (is (= #{s1 s3 s4}
                   (set (istorage/read store
                                       {:query '[:find ?id
                                                 :where
                                                 [?e :schedules/id ?id]]
                                        :xform (map first)}))))))))))
