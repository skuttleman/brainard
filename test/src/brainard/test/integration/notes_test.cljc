(ns brainard.test.integration.notes-test
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.uuids :as uuids]
    [brainard.ds :as-alias bds]
    [brainard.infra.db.datascript :as ds]
    [brainard.notes.api.core :as api.notes]
    [brainard.api.storage.core :as storage]
    [brainard.test.system :as tsys]
    [clojure.test :refer [deftest is testing]]
    brainard.infra.system)
  (:import
    (java.util Date)))

(deftest save!-test
  (tsys/with-system [{::bds/keys [client] ::b/keys [storage]} nil]
    (testing "when saving a note"
      (let [note-id (uuids/random)
            date-time (Date.)]
        (storage/execute! storage {::storage/type   ::api.notes/save!
                                   :notes/id        note-id
                                   :notes/body      "some body"
                                   :notes/context   "A Context"
                                   :notes/tags      #{:one :two :three}
                                   :notes/timestamp date-time})
        (testing "saves the note to datascript"
          (let [note (-> client
                         (ds/query '[:find (pull ?e [:notes/id
                                                     :notes/body
                                                     :notes/context
                                                     :notes/tags
                                                     :notes/timestamp])
                                     :in $ ?id
                                     :where [?e :notes/id ?id]]
                                   note-id)
                         ffirst
                         (update :notes/tags set))]
            (is (= {:notes/id        note-id
                    :notes/body      "some body"
                    :notes/context   "A Context"
                    :notes/tags      #{:one :two :three}
                    :notes/timestamp date-time}
                   note))))
        (testing "and when updating and retracting tags"
          (storage/execute! storage {::storage/type     ::api.notes/save!
                                     :notes/id          note-id
                                     :notes/context     "different context"
                                     :notes/tags!remove #{:one :two}
                                     :notes/tags        #{:four :five :six}})
          (testing "updates the note in datascript"
            (let [note (-> client
                           (ds/query '[:find (pull ?e [:notes/id
                                                       :notes/body
                                                       :notes/context
                                                       :notes/tags
                                                       :notes/timestamp])
                                       :in $ ?id
                                       :where [?e :notes/id ?id]]
                                     note-id)
                           ffirst
                           (update :notes/tags set))]
              (is (= {:notes/id        note-id
                      :notes/body      "some body"
                      :notes/context   "different context"
                      :notes/tags      #{:three :four :five :six}
                      :notes/timestamp date-time}
                     note)))))))))

(deftest get-notes-test
  (testing "when there are saved notes"
    (tsys/with-system [{::bds/keys [client] ::b/keys [storage]} nil]
      (let [[id-1 id-2 id-3 id-4] (repeatedly uuids/random)]
        (ds/transact! client [{:notes/id      id-1
                               :notes/context "a"
                               :notes/tags    #{:a :b :d}}
                              {:notes/id      id-2
                               :notes/context "a"
                               :notes/tags    #{:a :c}}
                              {:notes/id      id-3
                               :notes/context "b"
                               :notes/tags    #{:b :c :d}}
                              {:notes/id      id-4
                               :notes/context "b"
                               :notes/tags    #{:d}}])
        (testing "and when querying notes"
          (testing "finds notes by context"
            (let [results (into #{}
                                (map #(update % :notes/tags set))
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
                                (map #(update % :notes/tags set))
                                (storage/query storage {::storage/type ::api.notes/get-notes
                                                        :notes/tags    #{:c :d}}))]
              (is (= #{{:notes/id      id-3
                        :notes/context "b"
                        :notes/tags    #{:b :c :d}}}
                     results))))
          (testing "finds notes by context and tags"
            (let [results (into #{}
                                (map #(update % :notes/tags set))
                                (storage/query storage {::storage/type ::api.notes/get-notes
                                                        :notes/context "b"
                                                        :notes/tags    #{:b}}))]
              (is (= #{{:notes/id      id-3
                        :notes/context "b"
                        :notes/tags    #{:b :c :d}}}
                     results)))))

        (testing "and when querying a note that doesn't exist"
          (testing "returns nil"
            (is (nil? (storage/query storage {::storage/type ::api.notes/get-note
                                              :notes/id      (uuids/random)})))))))))
