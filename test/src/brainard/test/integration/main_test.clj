(ns brainard.test.integration.main-test
  (:require
    [brainard :as-alias b]
    [brainard.api.storage.core :as storage]
    [brainard.attachments.api.core :as api.attachments]
    [brainard.main :as main]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [duct.core.env :as duct.env]
    [integrant.core :as ig]))

(deftest cleanup-orphaned-artifacts!-test
  (let [sys (binding [duct.env/*env* (assoc duct.env/*env*
                                            "SERVER_PORT"
                                            (str (+ 9000 (rand-int 1000))))]
              (main/start! "duct/test.edn" [:duct.profile/base :duct.profile/test]))]
    (try
      (let [obj-storage (val (ig/find-derived-1 sys ::b/obj-storage))
            attachment-id (random-uuid)
            res (io/resource "fixtures/sample.txt")
            stream (io/file res)]
        (testing "when saving an orphaned artifact"
          (storage/execute! obj-storage {::storage/type            ::api.attachments/upload!
                                         :attachments/id           attachment-id
                                         :attachments/content-type "plain/text"
                                         :attachments/stream       stream})
          (testing "can download the artifact"
            (is (storage/query obj-storage {::storage/type  ::api.attachments/download
                                            :attachments/id attachment-id})))

          (testing "and when cleaning up orphaned artifacts"
            (main/cleanup-orphaned-artifacts! sys)
            (testing "cannot download the artifact"
              (is (nil? (storage/query obj-storage {::storage/type  ::api.attachments/download
                                                    :attachments/id attachment-id}))))

            (testing "and when there are no artifacts"
              (testing "returns nil"
                (is (nil? (main/cleanup-orphaned-artifacts! sys)))))

            (testing "and when the cleanup fails"
              (testing "returns nil"
                (is (nil? (main/cleanup-orphaned-artifacts! nil))))))))
      (finally
        (ig/halt! sys)))))
