(ns brainard.infra.search.store-test
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.infra.search.store :as search]
    [clojure.test :refer [deftest is testing]]
    [slag.utils.uuids :as uuids]))

(deftest ->NoteSearchStore-test
  (testing "when creating a store"
    (let [store (search/->NoteSearchStore (search/->mem-index))
          [n1 n2] (repeatedly uuids/random)]
      (testing "and when adding to the index"
        (istorage/write! store [{:action :create
                                 :doc    {:id      (str n1)
                                          :body    "the body"
                                          :context "Some Context"}}
                                {:action :create
                                 :doc    {:id      (str n2)
                                          :body    "the other body"
                                          :context "some context also"}}])

        (testing "and when searching the index"
          (testing "and when there are matches"
            (let [results1 (istorage/read store
                                          {:action :search
                                           :terms  #{"body"}})
                  results2 (istorage/read store
                                          {:action :search
                                           :terms  #{"also" "other"}})]
              (testing "returns the results"
                (is (= #{n1 n2}
                       (into #{} (map (comp :notes/id :hit)) results1)))
                (is (= #{n2}
                       (into #{} (map (comp :notes/id :hit)) results2))))))

          (testing "and when there are no matches"
            (testing "returns no results"
              (is (empty? (istorage/read store
                                         {:action :search
                                          :terms  #{"fluffy"}}))))))

        (testing "and when getting suggestions from the index"
          (testing "and when there are matches"
            (let [results1 (istorage/read store
                                          {:action :suggest
                                           :field  :context
                                           :prefix "some context"})
                  results2 (istorage/read store
                                          {:action :suggest
                                           :field  :context
                                           :prefix "some context a"})]
              (testing "returns the results"
                (is (= #{n1 n2}
                       (into #{} (map (comp :notes/id :hit)) results1)))
                (is (= #{n2}
                       (into #{} (map (comp :notes/id :hit)) results2)))))

            (testing "and when there are no matches"
              (testing "returns no results"
                (is (empty? (istorage/read store
                                           {:action :suggest
                                            :field  :context
                                            :prefix "the body"})))))))

        (testing "and when updating a note"
          (istorage/write! store [{:action :update
                                   :doc    {:id      (str n1)
                                            :body    "the new body"
                                            :context "Some New Context"}}])

          (testing "and when searching the index"
            (testing "and when there are matches"
              (let [results (istorage/read store
                                           {:action :search
                                            :terms  #{"new"}})]
                (testing "returns the results"
                  (is (= #{n1}
                         (into #{} (map (comp :notes/id :hit)) results)))))))

          (testing "and when getting suggestions from the index"
            (testing "and when there are matches"
              (let [results1 (istorage/read store
                                            {:action :suggest
                                             :field  :context
                                             :prefix "some"})
                    results2 (istorage/read store
                                            {:action :suggest
                                             :field  :context
                                             :prefix "some new"})]
                (testing "returns the results"
                  (is (= #{n1 n2}
                         (into #{} (map (comp :notes/id :hit)) results1)))
                  (is (= #{n1}
                         (into #{} (map (comp :notes/id :hit)) results2))))))

            (testing "and when there are no matches"
              (testing "returns no results"
                (is (empty? (istorage/read store
                                           {:action :suggest
                                            :field  :context
                                            :prefix "the body"})))))))

        (testing "and when deleting a note"
          (istorage/write! store [{:action :delete
                                   :id     (str n1)}])

          (testing "and when searching the index"
            (testing "and when there are no matches"
              (testing "returns no results"
                (is (empty? (istorage/read store
                                           {:action :search
                                            :terms  #{"new"}}))))))

          (testing "and when getting suggestions from the index"
            (testing "and when there are matches"
              (let [results1 (istorage/read store
                                            {:action :suggest
                                             :field  :context
                                             :prefix "some"})
                    results2 (istorage/read store
                                            {:action :suggest
                                             :field  :context
                                             :prefix "some new"})]
                (testing "returns the results"
                  (is (= #{n2}
                         (into #{} (map (comp :notes/id :hit)) results1)))
                  (is (empty? results2)))))))))))
