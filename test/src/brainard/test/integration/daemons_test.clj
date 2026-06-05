(ns brainard.test.integration.daemons-test
  (:require
    [brainard :as-alias b]
    [brainard.api.storage.core :as storage]
    [brainard.attachments.api.core :as api.attachments]
    [brainard.infra.system.daemons :as daemons]
    [brainard.test.harness.integration.system :as tsys]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]))

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
