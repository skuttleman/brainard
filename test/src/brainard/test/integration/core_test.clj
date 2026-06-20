(ns brainard.test.integration.core-test
  (:require
   [brainard :as-alias b]
   [brainard.schedules.api.core :as api.sched]
   [brainard.test.harness.integration.http :as thttp]
   [brainard.test.harness.integration.system :as tsys]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [slag.utils.uuids :as uuids]
   [workspace-nodes :as-alias ws]))

(deftest notes-integration-test
  (tsys/with-app [{::b/keys [apis]} nil]
    (letfn [(http [request]
              (thttp/request request apis))]
      (testing "when creating a note"
        (let [response (http {:method :post
                              :uri    "/api/notes"
                              :body   {:notes/context   "Context1"
                                       :notes/tags      #{:one :three}
                                       :notes/pinned?   false
                                       :notes/archived? false
                                       :notes/body      "body of note1"}})
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
                  [{:notes/context   "Context1"
                    :notes/tags      #{:one}
                    :notes/pinned?   false
                    :notes/archived? false
                    :notes/body      "body of note2"}
                   {:notes/context   "Context2"
                    :notes/tags      #{:one :three}
                    :notes/pinned?   false
                    :notes/archived? false
                    :notes/body      "body of note3"}
                   {:notes/context   "Context2"
                    :notes/tags      #{:three}
                    :notes/pinned?   false
                    :notes/archived? false
                    :notes/body      "body of note4"}
                   {:notes/context   "Context2"
                    :notes/tags      #{}
                    :notes/pinned?   false
                    :notes/archived? false
                    :notes/body      "body of note5"}])

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
                                    :body   {:notes/id        "ignored"
                                             :notes/tags      #{:two}
                                             :notes/old-tags  #{:one}
                                             :notes/pinned?   true
                                             :notes/archived? false}})
                    note (-> response :body :data)]
                (testing "returns the updated note"
                  (is (thttp/success? response))
                  (is (= {:notes/id          note1-id
                          :notes/context     "Context1"
                          :notes/body        "body of note1"
                          :notes/tags        #{:three :two}
                          :notes/pinned?     true
                          :notes/archived?   false
                          :notes/timestamp   (:notes/timestamp note)
                          :notes/attachments #{}
                          :notes/todos       #{}
                          :notes/links       #{}}
                         note)))

                (testing "and when searching for pinned notes"
                  (let [response (http {:method :get
                                        :uri    "/api/notes?pinned"})
                        notes (-> response :body :data)]
                    (testing "returns pinned notes"
                      (is (thttp/success? response))
                      (is (= [{:notes/id          note1-id
                               :notes/context     "Context1"
                               :notes/body        "body of note1"
                               :notes/tags        #{:three :two}
                               :notes/pinned?     true
                               :notes/archived?   false
                               :notes/timestamp   (:notes/timestamp note)
                               :notes/attachments #{}
                               :notes/todos       #{}}]
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
  (tsys/with-app [{::b/keys [apis]} nil]
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
                                                             :notes/archived?   false
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
                     :body   {:notes/old-attachments #{(:attachments/id attachment)}}}
                    http
                    :body
                    :data)
                (testing "does not delete the uploaded file"
                  (let [download (http {:method :get
                                        :uri    (str "/attachments/" (:attachments/id attachment))})]
                    (is (thttp/success? download))
                    (is (= (slurp (io/resource "fixtures/sample.txt"))
                           (:body download)))))))))))))

(deftest schedules-integration-test
  (tsys/with-app [{::b/keys [apis]} nil]
    (letfn [(http [request] (thttp/request request apis))]
      (let [note-id (-> (http {:method :post
                               :uri    "/api/notes"
                               :body   {:notes/context   "ctx"
                                        :notes/body      "body"
                                        :notes/pinned?   false
                                        :notes/archived? false
                                        :notes/tags      #{}}})
                        :body
                        :data
                        :notes/id)]
        (testing "when creating a schedule"
          (let [response (http {:method :post
                                :uri    "/api/schedules"
                                :body   {:schedules/note-id note-id
                                         :schedules/weekday :monday}})
                schedules (-> response :body :data)]
            (testing "returns the created schedule"
              (is (thttp/success? response))
              (is (= [{:schedules/note-id note-id
                       :schedules/weekday :monday}]
                     (->> schedules
                          (map #(select-keys % #{:schedules/note-id :schedules/weekday}))))))

            (testing "and when deleting the schedule"
              (let [delete-response (http {:method :delete
                                           :uri    (str "/api/schedules/" (:schedules/id (first schedules)))})]
                (testing "succeeds"
                  (is (thttp/success? delete-response))
                  (is (= {:data []} (:body delete-response))))
                (testing "and the note no longer has the schedule"
                  (is (empty? (-> (http {:method :get
                                         :uri    (str "/api/notes/" note-id)})
                                  :body
                                  :data
                                  :notes/schedules))))))))

        (testing "when creating an invalid schedule"
          (let [response (http {:method :post
                                :uri    "/api/schedules"
                                :body   {:schedules/note-id note-id}})
                errors (-> response :body :errors)]
            (testing "returns a validation error"
              (is (thttp/client-error? response))
              (is (seq errors)))))))))

(deftest note-delete-cascades-test
  (tsys/with-app [{::b/keys [apis]} nil]
    (letfn [(http [request] (thttp/request request apis))]
      (let [note-id (-> (http {:method :post
                               :uri    "/api/notes"
                               :body   {:notes/context   "ctx"
                                        :notes/body      "body"
                                        :notes/pinned?   false
                                        :notes/archived? false
                                        :notes/tags      #{}}})
                        :body
                        :data
                        :notes/id)]
        (http {:method :post
               :uri    "/api/schedules"
               :body   {:schedules/note-id note-id
                        :schedules/weekday :monday}})
        (testing "when deleting a note with a schedule"
          (http {:method :delete
                 :uri    (str "/api/notes/" note-id)})
          (testing "also deletes associated schedules"
            (is (empty? (api.sched/get-by-note-id (:schedules apis) note-id)))))))))

(deftest workspace-integration-test
  (tsys/with-app [{::b/keys [apis]} nil]
    (letfn [(http [request] (thttp/request request apis))]
      (testing "when fetching an empty workspace"
        (let [response (http {:method :get :uri "/api/workspace-nodes"})]
          (testing "returns an empty list"
            (is (thttp/success? response))
            (is (empty? (-> response :body :data))))))

      (testing "when creating a workspace node"
        (let [response (http {:method :post
                              :uri    "/api/workspace-nodes"
                              :body   {::ws/content "root node"}})
              nodes (-> response :body :data)]
          (testing "returns the workspace"
            (is (thttp/success? response))
            (is (= ["root node"] (map ::ws/content nodes))))

          (testing "and when updating the node"
            (let [update-response (http {:method :patch
                                         :uri    (str "/api/workspace-nodes/" (::ws/id (first nodes)))
                                         :body   {::ws/content "updated content"}})
                  nodes (-> update-response :body :data)]
              (testing "returns the updated workspace"
                (is (thttp/success? update-response))
                (is (= ["updated content"] (map ::ws/content nodes))))))

          (testing "and when deleting the node"
            (let [delete-response (http {:method :delete
                                         :uri    (str "/api/workspace-nodes/" (::ws/id (first nodes)))})
                  nodes (-> delete-response :body :data)]
              (testing "succeeds"
                (is (thttp/success? delete-response)))
              (testing "returns the empty workspace"
                (is (empty? nodes))))))

        (testing "when creating an invalid workspace node"
          (let [response (http {:method :post
                                :uri    "/api/workspace-nodes"
                                :body   {}})
                errors (-> response :body :errors)]
            (testing "returns a validation error"
              (is (thttp/client-error? response))
              (is (seq errors)))))))))
