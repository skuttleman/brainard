(ns brainard.infra.store.queries-test
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    brainard.infra.store.events
    brainard.infra.store.queries))

(defn ^:private store-with-history [spec history]
  (let [store (defacto/create {} {})]
    (store/emit! store [::res/submitted spec])
    (store/emit! store [::res/succeeded spec history])
    store))

(deftest notes-history-reconstruction-test
  (testing "when the resource is not loaded"
    (let [spec  [::specs/note#history (random-uuid)]
          store (defacto/create {} {})]
      (is (nil? (store/query store [:notes.history/?:reconstruction spec])))))

  (testing "when there are no history entries"
    (let [spec  [::specs/note#history (random-uuid)]
          store (store-with-history spec [])]
      (is (= {} (store/query store [:notes.history/?:reconstruction spec])))))

  (testing "with a single history entry"
    (let [h1    (random-uuid)
          spec  [::specs/note#history (random-uuid)]
          store (store-with-history spec [{:notes/history-id h1
                                           :notes/saved-at   #inst "2024-01-01"
                                           :notes/changes    {:notes/body    {:to "hello"}
                                                              :notes/context {:to "ctx-a"}
                                                              :notes/tags    {:added #{:x :y}}}}])
          result (store/query store [:notes.history/?:reconstruction spec])]
      (is (= "hello" (get-in result [h1 :notes/body])))
      (is (= "ctx-a" (get-in result [h1 :notes/context])))
      (is (= #{:x :y} (get-in result [h1 :notes/tags])))))

  (testing "with multiple history entries"
    (let [[h1 h2 h3] (repeatedly random-uuid)
          spec       [::specs/note#history (random-uuid)]
          store      (store-with-history spec [{:notes/history-id h1
                                                :notes/saved-at   #inst "2024-01-01"
                                                :notes/changes    {:notes/body    {:to "v1"}
                                                                   :notes/context {:to "ctx-a"}
                                                                   :notes/tags    {:added #{:a :b :c}}}}
                                               {:notes/history-id h2
                                                :notes/saved-at   #inst "2024-01-02"
                                                :notes/changes    {:notes/body {:to "v2"}
                                                                   :notes/tags {:added   #{:d}
                                                                                :removed #{:a}}}}
                                               {:notes/history-id h3
                                                :notes/saved-at   #inst "2024-01-03"
                                                :notes/changes    {:notes/body {:to "v3"}}}])
          result     (store/query store [:notes.history/?:reconstruction spec])]
      (testing "v1 has the initial state"
        (is (= "v1" (get-in result [h1 :notes/body])))
        (is (= #{:a :b :c} (get-in result [h1 :notes/tags]))))

      (testing "v2 applies changes and carries forward unchanged fields"
        (is (= "v2" (get-in result [h2 :notes/body])))
        (is (= #{:b :c :d} (get-in result [h2 :notes/tags])))
        (is (= "ctx-a" (get-in result [h2 :notes/context]))))

      (testing "v3 applies changes and carries forward unchanged fields"
        (is (= "v3" (get-in result [h3 :notes/body])))
        (is (= #{:b :c :d} (get-in result [h3 :notes/tags])))
        (is (= "ctx-a" (get-in result [h3 :notes/context]))))))

  (testing "with attachment history"
    (let [att-ref      123
          [h1 h2 h3]  (repeatedly random-uuid)
          spec         [::specs/note#history (random-uuid)]
          store        (store-with-history spec [{:notes/history-id h1
                                                  :notes/saved-at   #inst "2024-01-01"
                                                  :notes/changes    {:notes/attachments   {:added #{att-ref}}
                                                                     :attachments/changes {att-ref {:attachments/name {:to "original.txt"}}}}}
                                                 {:notes/history-id h2
                                                  :notes/saved-at   #inst "2024-01-02"
                                                  :notes/changes    {:attachments/changes {att-ref {:attachments/name {:from "original.txt"
                                                                                                                       :to   "renamed.txt"}}}}}
                                                 {:notes/history-id h3
                                                  :notes/saved-at   #inst "2024-01-03"
                                                  :notes/changes    {:notes/attachments   {:removed #{att-ref}}
                                                                     :attachments/changes {att-ref {:attachments/name {:from "renamed.txt"}}}}}])
          result       (store/query store [:notes.history/?:reconstruction spec])]
      (testing "v1 shows the attachment added"
        (is (contains? (get-in result [h1 :notes/attachments]) att-ref))
        (is (= {:added "original.txt"} (get-in result [h1 :attachments/changes att-ref]))))

      (testing "v2 shows the attachment renamed"
        (is (contains? (get-in result [h2 :notes/attachments]) att-ref))
        (is (= {:from "original.txt" :to "renamed.txt"} (get-in result [h2 :attachments/changes att-ref]))))

      (testing "v3 shows the attachment removed"
        (is (not (contains? (get-in result [h3 :notes/attachments]) att-ref)))
        (is (= {:removed "renamed.txt"} (get-in result [h3 :attachments/changes att-ref])))))))
