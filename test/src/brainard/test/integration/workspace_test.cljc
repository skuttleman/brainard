(ns brainard.test.integration.workspace-test
  (:require
    [brainard :as-alias b]
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.db.store :as ds]
    [brainard.test.system :as tsys]
    [brainard.workspace.api.core :as api.ws]
    [clojure.test :refer [deftest is testing]]
    [clojure.walk :as walk]
    [workspace-nodes :as-alias ws]
    brainard.infra.system))

(defn ^:private seed-tree! [conn]
  (let [[id1 id2 id3 id4 id5] (repeatedly uuids/random)]
    (ds/transact! conn
                  [{::ws/id       id1
                    ::ws/content  "root"
                    ::ws/index    0
                    ::ws/children [{::ws/id        id2
                                    ::ws/parent-id id1
                                    ::ws/content   "sub1"
                                    ::ws/index     0}
                                   {::ws/id        id3
                                    ::ws/parent-id id1
                                    ::ws/content   "sub2"
                                    ::ws/index     1
                                    ::ws/children  [{::ws/id        id4
                                                     ::ws/parent-id id3
                                                     ::ws/content   "sub-sub"
                                                     ::ws/index     0}]}
                                   {::ws/id        id5
                                    ::ws/parent-id id1
                                    ::ws/content   "sub3"
                                    ::ws/index     2}]}])
    [id1 id2 id3 id4 id5]))

(defn ^:private clean-tree
  ([tree]
   (clean-tree tree nil))
  ([tree keys]
   (let [keys (into #{::ws/content ::ws/children} keys)]
     (walk/postwalk (fn [x]
                      (cond-> x
                        (map? x) (select-keys keys)))
                    tree))))

(deftest create!-test
  (tsys/with-system [{::b/keys [IDBConn workspace-api]} nil]
    (let [[_ _ id3] (seed-tree! IDBConn)]
      (testing "when creating a node with no parent"
        (api.ws/create! workspace-api {::ws/content "parent-less"})
        (testing "appends the node to the root of the tree"
          (is (= [{::ws/content  "root"
                   ::ws/children [{::ws/content "sub1"}
                                  {::ws/content  "sub2"
                                   ::ws/children [{::ws/content "sub-sub"}]}
                                  {::ws/content "sub3"}]}
                  {::ws/content "parent-less"}]
                 (clean-tree (api.ws/get-tree workspace-api)))))

        (testing "and when creating a node with a parent"
          (api.ws/create! workspace-api {::ws/parent-id id3
                                         ::ws/content   "sub-sub2"})
          (testing "appends the node to the parent node"
            (is (= [{::ws/content  "root"
                     ::ws/children [{::ws/content "sub1"}
                                    {::ws/content  "sub2"
                                     ::ws/children [{::ws/content "sub-sub"}
                                                    {::ws/content "sub-sub2"}]}
                                    {::ws/content "sub3"}]}
                    {::ws/content "parent-less"}]
                   (clean-tree (api.ws/get-tree workspace-api))))))))))

(deftest delete!-test
  (tsys/with-system [{::b/keys [IDBConn storage workspace-api]} nil]
    (let [[_ _ id3 id4] (seed-tree! IDBConn)]
      (testing "when deleting a node"
        (api.ws/delete! workspace-api id3)
        (testing "removes the node from the tree"
          (is (= [{::ws/content  "root"
                   ::ws/children [{::ws/content "sub1"}
                                  {::ws/content "sub3"}]}]
                 (clean-tree (api.ws/get-tree workspace-api))))
          (is (nil? (storage/query storage {::storage/type ::api.ws/fetch-by-id
                                            ::ws/id        id3}))))

        (testing "removes its ancestor nodes"
          (is (nil? (storage/query storage {::storage/type ::api.ws/fetch-by-id
                                            ::ws/id        id4}))))))))

(deftest update!-test
  (tsys/with-system [{::b/keys [IDBConn workspace-api]} nil]
    (let [[id1 id2 id3 id4 id5] (seed-tree! IDBConn)]
      (testing "when updating a node's content"
        (api.ws/update! workspace-api id3 {::ws/content "new content"})
        (testing "changes the node in place"
          (is (= [{::ws/id       id1
                   ::ws/content  "root"
                   ::ws/children [{::ws/id        id2
                                   ::ws/parent-id id1
                                   ::ws/content   "sub1"}
                                  {::ws/id        id3
                                   ::ws/parent-id id1
                                   ::ws/content   "new content"
                                   ::ws/children  [{::ws/content   "sub-sub"
                                                    ::ws/id        id4
                                                    ::ws/parent-id id3}]}
                                  {::ws/id        id5
                                   ::ws/parent-id id1
                                   ::ws/content   "sub3"}]}]
                 (clean-tree (api.ws/get-tree workspace-api)
                             #{::ws/id ::ws/parent-id}))))

        (testing "and when reordering the node"
          (api.ws/update! workspace-api id3 {::ws/prev-sibling-id id5})
          (testing "moves the node to the new position"
            (is (= [{::ws/id       id1
                     ::ws/content  "root"
                     ::ws/children [{::ws/id        id2
                                     ::ws/parent-id id1
                                     ::ws/content   "sub1"}
                                    {::ws/id        id5
                                     ::ws/parent-id id1
                                     ::ws/content   "sub3"}
                                    {::ws/id        id3
                                     ::ws/parent-id id1
                                     ::ws/content   "new content"
                                     ::ws/children  [{::ws/content   "sub-sub"
                                                      ::ws/id        id4
                                                      ::ws/parent-id id3}]}]}]
                   (clean-tree (api.ws/get-tree workspace-api)
                               #{::ws/id ::ws/parent-id}))))

          (testing "and when moving the node to a different parent"
            (api.ws/update! workspace-api id3 {::ws/parent-id id5})
            (testing "moves the node to the new parent"
              (is (= [{::ws/id       id1
                       ::ws/content  "root"
                       ::ws/children [{::ws/id        id2
                                       ::ws/parent-id id1
                                       ::ws/content   "sub1"}
                                      {::ws/id        id5
                                       ::ws/parent-id id1
                                       ::ws/content   "sub3"
                                       ::ws/children [{::ws/id        id3
                                                       ::ws/parent-id id5
                                                       ::ws/content   "new content"
                                                       ::ws/children  [{::ws/content   "sub-sub"
                                                                        ::ws/id        id4
                                                                        ::ws/parent-id id3}]}]}]}]
                     (clean-tree (api.ws/get-tree workspace-api)
                                 #{::ws/id ::ws/parent-id}))))

            (testing "and when moving the node to its current parent"
              (api.ws/update! workspace-api id3 {::ws/parent-id id5})
              (testing "keeps the node in teh same position"
                (is (= [{::ws/id       id1
                         ::ws/content  "root"
                         ::ws/children [{::ws/id        id2
                                         ::ws/parent-id id1
                                         ::ws/content   "sub1"}
                                        {::ws/id        id5
                                         ::ws/parent-id id1
                                         ::ws/content   "sub3"
                                         ::ws/children [{::ws/id        id3
                                                         ::ws/parent-id id5
                                                         ::ws/content   "new content"
                                                         ::ws/children  [{::ws/content   "sub-sub"
                                                                          ::ws/id        id4
                                                                          ::ws/parent-id id3}]}]}]}]
                       (clean-tree (api.ws/get-tree workspace-api)
                                   #{::ws/id ::ws/parent-id})))))

            (testing "and when moving to be the child of its ancestor"
              (testing "makes no change"
                (let [expected (clean-tree (api.ws/get-tree workspace-api)
                                           #{::ws/id ::ws/parent-id})]
                  (api.ws/update! workspace-api id3 {::ws/parent-id id4})
                  (is (= expected (clean-tree (api.ws/get-tree workspace-api)
                                              #{::ws/id ::ws/parent-id})))))))))

      (testing "when updating a top-level node's content"
        (api.ws/update! workspace-api id1 {::ws/content "altered root"})
        (testing "changes the node in place"
          (is (= [{::ws/id       id1
                   ::ws/content  "altered root"
                   ::ws/children [{::ws/id        id2
                                   ::ws/parent-id id1
                                   ::ws/content   "sub1"}
                                  {::ws/id        id5
                                   ::ws/parent-id id1
                                   ::ws/content   "sub3"
                                   ::ws/children [{::ws/id        id3
                                                   ::ws/parent-id id5
                                                   ::ws/content   "new content"
                                                   ::ws/children  [{::ws/content   "sub-sub"
                                                                    ::ws/id        id4
                                                                    ::ws/parent-id id3}]}]}]}]
                 (clean-tree (api.ws/get-tree workspace-api)
                             #{::ws/id ::ws/parent-id}))))))))
