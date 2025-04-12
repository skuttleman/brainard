(ns brainard.test.integration.attachments-test
  (:require
    [brainard :as-alias b]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.uuids :as uuids]
    [brainard.attachments.api.core :as api.attachments]
    [brainard.api.storage.core :as storage]
    [brainard.test.system :as tsys]
    [clojure.test :refer [deftest is testing]])
  (:import
    (java.io ByteArrayInputStream)))

(deftest upload!-test
  (tsys/with-system [{::b/keys [attachments-api obj-storage storage]} nil]
    (testing "when uploading an attachment"
      (let [attachment (-> attachments-api
                           (api.attachments/upload! [{:attachments/content-type "some/type"
                                                      :attachments/filename     "some-file.txt"
                                                      :attachments/stream       (-> "some content"
                                                                                    (.getBytes "UTF-8")
                                                                                    ByteArrayInputStream.)}])
                           first)]
        (testing "saves the attachment to the db"
          (let [result (-> storage
                           (istorage/read {:query '[:find (pull ?e [:attachments/id
                                                                    :attachments/content-type
                                                                    :attachments/filename
                                                                    :attachments/name])
                                                    :in $ ?id
                                                    :where [?e :attachments/id ?id]]
                                           :args  [(:attachments/id attachment)]})
                           ffirst)]
            (is (= result attachment))))

        (testing "saves the attachment to object storage"
          (let [result (storage/query obj-storage {::storage/type  ::api.attachments/download
                                                   :attachments/id (:attachments/id attachment)})]
            (is (= {:Body          "some content"
                    :ContentLength (count "some content")
                    :ContentType   "some/type"}
                   (update result :Body slurp)))))))))

(deftest fetch-test
  (tsys/with-system [{::b/keys [attachments-api]} nil]
    (testing "when there is a saved attachment"
      (let [attachment (-> attachments-api
                           (api.attachments/upload! [{:attachments/content-type "some/type"
                                                      :attachments/filename     "some-file.txt"
                                                      :attachments/stream       (-> "some content"
                                                                                    (.getBytes "UTF-8")
                                                                                    ByteArrayInputStream.)}])
                           first)]
        (testing "finds attachment by id"
          (is (= {:attachments/id             (:attachments/id attachment)
                  :attachments/content-length (count "some content")
                  :attachments/content-type   "some/type"
                  :attachments/stream         "some content"}
                 (-> attachments-api
                     (api.attachments/fetch (:attachments/id attachment))
                     (update :attachments/stream slurp)))))))

    (testing "when there is no attachment"
      (let [result (api.attachments/fetch attachments-api (uuids/random))]
        (testing "returns nil"
          (is (nil? result)))))))
