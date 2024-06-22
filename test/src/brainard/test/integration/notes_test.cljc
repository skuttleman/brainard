(ns brainard.test.integration.notes-test
  (:require
    [brainard :as-alias b]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.uuids :as uuids]
    [brainard.ds :as-alias bds]
    [brainard.notes.api.core :as api.notes]
    [brainard.api.storage.core :as storage]
    [brainard.test.system :as tsys]
    [clojure.test :refer [deftest is testing]]
    brainard.infra.system))

(deftest save!-test
  (tsys/with-system [{::b/keys [storage]} nil]
    (testing "when saving a note"
      (let [note-id (uuids/random)]
        (storage/execute! storage {::storage/type   ::api.notes/create!
                                   :notes/id        note-id
                                   :notes/body      "some body"
                                   :notes/context   "A Context"
                                   :notes/tags      #{:one :two :three}
                                   :notes/pinned?   false})
        (testing "saves the note to datascript"
          (let [note (-> storage
                         (istorage/read {:query '[:find (pull ?e [:notes/id
                                                                  :notes/body
                                                                  :notes/context
                                                                  :notes/tags])
                                                  :in $ ?id
                                                  :where [?e :notes/id ?id]]
                                         :args  [note-id]})
                         ffirst
                         (update :notes/tags set))]
            (is (= {:notes/id        note-id
                    :notes/body      "some body"
                    :notes/context   "A Context"
                    :notes/tags      #{:one :two :three}}
                   note))))
        (testing "and when updating and retracting tags"
          (storage/execute! storage {::storage/type     ::api.notes/update!
                                     :notes/id          note-id
                                     :notes/context     "different context"
                                     :notes/tags!remove #{:one :two}
                                     :notes/tags        #{:four :five :six}})
          (testing "updates the note in datascript"
            (let [note (-> storage
                           (istorage/read {:query '[:find (pull ?e [:notes/id
                                                                    :notes/body
                                                                    :notes/context
                                                                    :notes/tags])
                                                    :in $ ?id
                                                    :where [?e :notes/id ?id]]
                                           :args  [note-id]})
                           ffirst
                           (update :notes/tags set))]
              (is (= {:notes/id        note-id
                      :notes/body      "some body"
                      :notes/context   "different context"
                      :notes/tags      #{:three :four :five :six}}
                     note)))))
        (testing "and when updating and pinning the note"
          (storage/execute! storage {::storage/type ::api.notes/update!
                                     :notes/id      note-id
                                     :notes/pinned? true})
          (testing "updates the note in datascript"
            (let [note (-> storage
                           (istorage/read {:query '[:find (pull ?e [:notes/id
                                                                    :notes/pinned?])
                                                    :in $ ?id
                                                    :where [?e :notes/id ?id]]
                                           :args  [note-id]})
                           ffirst)]
              (is (= {:notes/id      note-id
                      :notes/pinned? true}
                     note)))))))))

(deftest get-notes-test
  (testing "when there are saved notes"
    (tsys/with-system [{::b/keys [storage]} nil]
      (let [[id-1 id-2 id-3 id-4 id-5] (repeatedly uuids/random)]
        (istorage/write! storage
                         [{:notes/id      id-1
                           :notes/context "a"
                           :notes/tags    #{:a :b :d}}
                          {:notes/id      id-2
                           :notes/context "a"
                           :notes/tags    #{:a :c}}
                          {:notes/id      id-3
                           :notes/context "b"
                           :notes/tags    #{:b :c :d}
                           :notes/pinned? true}
                          {:notes/id      id-4
                           :notes/context "b"
                           :notes/tags    #{:d}}
                          {:notes/id      id-5
                           :notes/context "XYZ"
                           :notes/pinned? true}])
        (testing "and when querying notes"
          (testing "finds notes by context"
            (let [results (into #{}
                                (map #(-> %
                                          (update :notes/tags set)
                                          (dissoc :notes/timestamp)))
                                (storage/query storage {::storage/type ::api.notes/get-notes
                                                        :notes/context "a"}))]
              (is (= #{{:notes/id      id-1
                        :notes/context "a"
                        :notes/tags    #{:a :b :d}}
                       {:notes/id      id-2
                        :notes/context "a"
                        :notes/tags    #{:a :c}}}
                     results))))
          (testing "finds notes by tags"
            (let [results (into #{}
                                (map #(-> %
                                          (update :notes/tags set)
                                          (dissoc :notes/timestamp)))
                                (storage/query storage {::storage/type ::api.notes/get-notes
                                                        :notes/tags    #{:c :d}}))]
              (is (= #{{:notes/id      id-3
                        :notes/context "b"
                        :notes/pinned? true
                        :notes/tags    #{:b :c :d}}}
                     results))))
          (testing "finds notes by context and tags"
            (let [results (into #{}
                                (map #(-> %
                                          (update :notes/tags set)
                                          (dissoc :notes/timestamp)))
                                (storage/query storage {::storage/type ::api.notes/get-notes
                                                        :notes/context "b"
                                                        :notes/tags    #{:b}}))]
              (is (= #{{:notes/id      id-3
                        :notes/context "b"
                        :notes/pinned? true
                        :notes/tags    #{:b :c :d}}}
                     results))))
          (testing "finds pinned notes"
            (let [results (into #{}
                                (map #(-> %
                                          (update :notes/tags set)
                                          (dissoc :notes/timestamp)))
                                (storage/query storage {::storage/type ::api.notes/get-notes
                                                        :notes/pinned? true}))]
              (is (= #{{:notes/id      id-3
                        :notes/context "b"
                        :notes/pinned? true
                        :notes/tags    #{:b :c :d}}
                       {:notes/id      id-5
                        :notes/context "XYZ"
                        :notes/pinned? true
                        :notes/tags    #{}}}
                     results))))

          (testing "and when deleting a note"
            (storage/execute! storage
                              {::storage/type ::api.notes/delete!
                               :notes/id      id-1})
            (testing "and when querying for a deleted note"
              (testing "does not find the note"
                (is (nil? (storage/query storage {::storage/type ::api.notes/get-note
                                                  :notes/id      id-1})))))))

        (testing "and when querying a note that doesn't exist"
          (testing "returns nil"
            (is (nil? (storage/query storage {::storage/type ::api.notes/get-note
                                              :notes/id      (uuids/random)})))))))))

#?(:clj
   (deftest get-note-history-test
     (testing "when there is a note"
       (tsys/with-system [{::b/keys [storage]} nil]
         (let [note-id (uuids/random)]
           (istorage/write! storage
                            [{:notes/id      note-id
                              :notes/context "a"
                              :notes/tags    #{:a :b :c}
                              :notes/pinned? false}])
           (testing "and when querying the note's history"
             (let [history (storage/query storage {::storage/type ::api.notes/get-note-history
                                                   :notes/id      note-id})]
               (testing "returns the history of the note"
                 (is (= [{:notes/context {:to "a"}
                          :notes/pinned? {:to false}
                          :notes/tags    {:added #{:a :b :c}}
                          :notes/id      {:to note-id}}]
                        (map :notes/changes history))))))

           (testing "and when the note changes"
             (storage/execute! storage {::storage/type ::api.notes/update!
                                        :notes/id      note-id
                                        :notes/context "diff context"
                                        :notes/pinned? false
                                        :notes/tags    #{:a :b :c :d}})
             (storage/execute! storage {::storage/type     ::api.notes/update!
                                        :notes/id          note-id
                                        :notes/tags        #{:b :d :e}
                                        :notes/pinned?     true
                                        :notes/tags!remove #{:a :c}})
             (storage/execute! storage {::storage/type ::api.notes/update!
                                        :notes/id      note-id
                                        :notes/context "diff context"
                                        :notes/pinned? false})
             (testing "and when querying the note's history"
               (let [history (storage/query storage {::storage/type ::api.notes/get-note-history
                                                     :notes/id      note-id})]
                 (testing "returns the history of the note"
                   (is (= [{:notes/id      {:to note-id}
                            :notes/context {:to "a"}
                            :notes/pinned? {:to false}
                            :notes/tags    {:added #{:a :b :c}}}
                           {:notes/context {:from "a"
                                            :to   "diff context"}
                            :notes/tags    {:added #{:d}}}
                           {:notes/pinned? {:from false
                                            :to   true}
                            :notes/tags    {:added   #{:e}
                                            :removed #{:a :c}}}
                           {:notes/pinned? {:from true
                                            :to   false}}]
                          (map :notes/changes history))))))))))))
