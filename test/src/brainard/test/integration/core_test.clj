(ns brainard.test.integration.core-test
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.uuids :as uuids]
    [brainard.test.http :as thttp]
    [brainard.test.system :as tsys]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]))

(deftest notes-integration-test
  (tsys/with-system [{::b/keys [apis]} nil]
    (letfn [(http [request]
              (thttp/request request apis))]
      (testing "when creating a note"
        (let [response (http {:method :post
                              :uri    "/api/notes"
                              :body   {:notes/context "Context1"
                                       :notes/tags    #{:one :three}
                                       :notes/pinned? false
                                       :notes/body    "body of note1"}})
              {note1-id :notes/id :as note1} (-> response :body :data)]
          (testing "returns the created note"
            (is (thttp/success? response))
            (is (= {:notes/context "Context1"
                    :notes/tags    #{:one :three}
                    :notes/body    "body of note1"}
                   (select-keys note1 #{:notes/context :notes/tags :notes/body}))))

          (testing "and when fetching the note by id"
            (let [response (http {:method :get
                                  :uri    (str "/api/notes/" note1-id)})]
              (testing "finds the note"
                (is (thttp/success? response))
                (is (= {:notes/context "Context1"
                        :notes/tags    #{:one :three}
                        :notes/body    "body of note1"}
                       (select-keys (-> response :body :data)
                                    #{:notes/context :notes/tags :notes/body}))))))

          (testing "and when creating more notes"
            (run! #(http {:method :post
                          :uri    "/api/notes"
                          :body   %})
                  [{:notes/context "Context1"
                    :notes/tags    #{:one}
                    :notes/pinned? false
                    :notes/body    "body of note2"}
                   {:notes/context "Context2"
                    :notes/tags    #{:one :three}
                    :notes/pinned? false
                    :notes/body    "body of note3"}
                   {:notes/context "Context2"
                    :notes/tags    #{:three}
                    :notes/pinned? false
                    :notes/body    "body of note4"}
                   {:notes/context "Context2"
                    :notes/tags    #{}
                    :notes/pinned? false
                    :notes/body    "body of note5"}])

            (testing "and when searching for matching tags"
              (let [response (http {:method :get
                                    :uri    "/api/notes?tags=one&tags=three"})
                    notes (-> response :body :data)]
                (testing "selects the correct notes"
                  (is (thttp/success? response))
                  (is (= #{"body of note1" "body of note3"}
                         (into #{} (map :notes/body) notes))))))

            (testing "and when searching for non-matching tags"
              (let [response (http {:method :get
                                    :uri    "/api/notes?tags=one&tags=four"})
                    notes (-> response :body :data)]
                (testing "does not select any notes"
                  (is (thttp/success? response))
                  (is (empty? notes)))))

            (testing "and when searching for a matching context"
              (let [response (http {:method :get
                                    :uri    "/api/notes?context=Context2"})
                    notes (-> response :body :data)]
                (testing "selects the correct notes"
                  (is (thttp/success? response))
                  (is (= #{"body of note3" "body of note4" "body of note5"}
                         (into #{} (map :notes/body) notes))))))

            (testing "and when searching for context and tags"
              (let [response (http {:method :get
                                    :uri    "/api/notes?context=Context2&tags=three"})
                    notes (-> response :body :data)]
                (testing "selects the correct notes"
                  (is (thttp/success? response))
                  (is (= #{"body of note3" "body of note4"}
                         (into #{} (map :notes/body) notes))))))

            (testing "and when searching for a non-matching context + tags combination"
              (let [response (http {:method :get
                                    :uri    "/api/notes?tags=two&context=Context2"})
                    notes (-> response :body :data)]
                (testing "does not select any notes"
                  (is (thttp/success? response))
                  (is (empty? notes)))))

            (testing "and when updating a note"
              (let [response (http {:method :patch
                                    :uri    (str "/api/notes/" note1-id)
                                    :body   {:notes/id          "ignored"
                                             :notes/tags        #{:two}
                                             :notes/tags!remove #{:one}
                                             :notes/pinned?     true}})
                    note (-> response :body :data)]
                (testing "returns the updated note"
                  (is (thttp/success? response))
                  (is (= {:notes/id          note1-id
                          :notes/context     "Context1"
                          :notes/body        "body of note1"
                          :notes/tags        #{:three :two}
                          :notes/pinned?     true
                          :notes/timestamp   (:notes/timestamp note)
                          :notes/attachments #{}}
                         note)))

                (testing "and when searching for pinned notes"
                  (let [response (http {:method :get
                                        :uri    "/api/notes?pinned=true"})
                        notes (-> response :body :data)]
                    (testing "returns pinned notes"
                      (is (thttp/success? response))
                      (is (= [{:notes/id          note1-id
                               :notes/context     "Context1"
                               :notes/body        "body of note1"
                               :notes/tags        #{:three :two}
                               :notes/pinned?     true
                               :notes/timestamp   (:notes/timestamp note)
                               :notes/attachments #{}}]
                             notes))))))

              (testing "and when attempting an invalid update"
                (let [response (http {:method :patch
                                      :uri    (str "/api/notes/" note1-id)
                                      :body   {:notes/tags #{"seven"}}})
                      errors (-> response :body :errors)]
                  (testing "throws an error"
                    (is (thttp/client-error? response))
                    (is (= [{:details {:notes/tags #{["should be a keyword"]}}}]
                           (map #(select-keys % #{:details}) errors))))))

              (testing "and when updating a note that does not exist"
                (let [response (http {:method :patch
                                      :uri    (str "/api/notes/" (uuids/random))
                                      :body   {:notes/tags #{:tag}}})
                      errors (-> response :body :errors)]
                  (testing "throws an error"
                    (is (thttp/client-error? response))
                    (is (= [{:message "Not found" :code :UNKNOWN_RESOURCE}]
                           errors)))))

              (testing "and when fetching the note by id"
                (let [response (http {:method :get
                                      :uri    (str "/api/notes/" note1-id)})]
                  (testing "finds the updated note"
                    (is (thttp/success? response))
                    (is (= {:notes/context "Context1"
                            :notes/tags    #{:two :three}
                            :notes/body    "body of note1"}
                           (select-keys (-> response :body :data)
                                        #{:notes/context :notes/tags :notes/body}))))))

              (testing "and when deleting the note by id"
                (let [response (http {:method :delete
                                      :uri    (str "/api/notes/" note1-id)})]
                  (testing "deletes the note"
                    (is (thttp/success? response))))

                (testing "and when fetching the note by id"
                  (let [response (http {:method :get
                                        :uri    (str "/api/notes/" note1-id)})]
                    (testing "does not find the note"
                      (is (thttp/client-error? response))))))))))

      (testing "when creating an invalid note"
        (let [response (http {:method :post
                              :uri    "/api/notes"
                              :body   {:notes/tags #{:one}}})
              errors (-> response :body :errors)]
          (testing "throws an error"
            (is (thttp/client-error? response))
            (is (= [{:details {:notes/context ["missing required key"]
                               :notes/body    ["missing required key"]
                               :notes/pinned? ["missing required key"]}}]
                   (map #(select-keys % #{:details}) errors))))))

      (testing "and when fetching a note by non-existing id"
        (let [response (http {:method :get
                              :uri    (str "/api/notes/" (uuids/random))})
              errors (-> response :body :errors)]
          (testing "throws an error"
            (is (thttp/client-error? response))
            (is (= #{:UNKNOWN_RESOURCE}
                   (into #{} (map :code) errors)))))))))

(deftest attachments-integration-test
  (tsys/with-system [{::b/keys [apis]} nil]
    (letfn [(http [request]
              (thttp/request request apis))]
      (testing "when uploading an attachment"
        (let [response (http {:method           :post
                              :uri              "/api/attachments"
                              :multipart-params {:files ["fixtures/sample.txt"]}})
              attachment (-> response
                             :body
                             :data
                             first
                             (assoc :attachments/name "some other name"))]
          (testing "successfully uploads the attachment"
            (is (thttp/success? response)))

          (testing "and when downloading the attachment"
            (let [download (http {:method :get
                                  :uri    (str "/attachments/" (:attachments/id attachment))})]
              (testing "successfully fetches the attachment"
                (is (thttp/success? download))
                (is (= (slurp (io/resource "fixtures/sample.txt"))
                       (:body download))))))

          (testing "and when creating a note"
            (let [{note-id :notes/id :as note} (-> {:method :post
                                                    :uri    "/api/notes"
                                                    :body   {:notes/context     "Context1"
                                                             :notes/tags        #{:one :three}
                                                             :notes/pinned?     false
                                                             :notes/body        "body of note"
                                                             :notes/attachments [attachment]}}
                                                   http
                                                   :body
                                                   :data)]
              (testing "saves the attachment"
                (is (= attachment (-> note :notes/attachments first))))

              (testing "and when fetching the note"
                (let [note (-> {:method :get
                                :uri    (str "/api/notes/" note-id)}
                               http
                               :body
                               :data)]
                  (testing "includes the attachment"
                    (is (= attachment (-> note :notes/attachments first))))))

              (testing "and when removing the attachment"
                (-> {:method :patch
                     :uri    (str "/api/notes/" note-id)
                     :body   {:notes/attachments!remove #{(:attachments/id attachment)}}}
                    http
                    :body
                    :data)
                (testing "does not delete the uploaded file"
                  (let [download (http {:method :get
                                        :uri    (str "/attachments/" (:attachments/id attachment))})]
                    (is (thttp/success? download))
                    (is (= (slurp (io/resource "fixtures/sample.txt"))
                           (:body download)))))))))))))
