(ns brainard.test.integration.history-test
  (:require
   [brainard :as-alias b]
   [brainard.api.storage.core :as storage]
   [brainard.notes.api.core :as-alias api.notes]
   [brainard.test.harness.integration.system :as tsys]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [slag.utils.edn :as edn]))

(deftest history-query-test
  (tsys/with-app [{::b/keys [storage]} nil]
    (testing "when generating history"
      (let [txs (->> (io/resource "fixtures/seed/history.edn")
                     edn/read
                     (sort-by key)
                     vals)]
        (run! (partial storage/execute! storage) txs)

        (testing "and when querying the history"
          (let [note-id (:notes/id (ffirst txs))
                [t1 t2 t3] (->> txs
                                (mapcat identity)
                                (mapcat :notes/todos)
                                (keep :todos/id)
                                distinct)
                [a1 a2 a3] (->> txs
                                (mapcat identity)
                                (mapcat :notes/attachments)
                                (keep :attachments/id)
                                distinct)
                [c1 c2 c3 c4 c5 c6 c7 c8 c9] (->> (storage/query
                                                   storage
                                                   {::storage/type ::api.notes/get-note-history
                                                    :notes/id      note-id})
                                                  (map :notes/changes))]

            (testing "returns the first change"
              (is (= {:notes/id        {:to note-id}
                      :notes/context   {:to "Some context"}
                      :notes/body      {:to "Some body"}
                      :notes/pinned?   {:to true}
                      :notes/archived? {:to false}
                      :notes/tags      {:added #{:bar :baz/quux :foo}}}
                     (-> c1 (dissoc :notes/todos :todos/changes))))
              (is (= (-> c1 :notes/todos :added)
                     (-> c1 :todos/changes keys set)))
              (is (= #{{:todos/id         {:to t1}
                        :todos/text       {:to "Do a thing"}
                        :todos/completed? {:to false}}}
                     (->> c1
                          :notes/todos
                          :added
                          (into #{} (map (fn [id] (get-in c1 [:todos/changes id]))))))))

            (testing "returns the second change"
              (is (= {:notes/body {:from "Some body"
                                   :to   "Some edited body goes here"}
                      :notes/tags {:removed #{:bar}}}
                     (-> c2 (dissoc :notes/attachments :attachments/changes))))
              (is (= (-> c2 :notes/attachments :added)
                     (-> c2 :attachments/changes keys set)))
              (is (= #{{:attachments/id           {:to a1}
                        :attachments/content-type {:to "plain/text"}
                        :attachments/name         {:to "sample.txt"}
                        :attachments/filename     {:to "sample.txt"}}
                       {:attachments/id           {:to a2}
                        :attachments/filename     {:to "some-pdf.pdf"}
                        :attachments/content-type {:to "application/pdf"}
                        :attachments/name         {:to "some-pdf.pdf"}}}
                     (->> c2
                          :notes/attachments
                          :added
                          (into #{} (map (fn [id] (get-in c2 [:attachments/changes id]))))))))

            (testing "returns the third change"
              (is (empty? (-> c3 (dissoc :notes/todos :notes/attachments :todos/changes :attachments/changes))))
              (is (= (-> c3 :notes/todos :added)
                     (-> c3 :todos/changes keys set)))
              (is (= (-> c3 :notes/attachments :added)
                     (-> c3 :attachments/changes keys set)))
              (is (= #{{:todos/id         {:to t2}
                        :todos/completed? {:to false}
                        :todos/text       {:to "Do another thing"}}}
                     (->> c3
                          :notes/todos
                          :added
                          (into #{} (map (fn [id] (get-in c3 [:todos/changes id])))))))
              (is (= #{{:attachments/id           {:to a3}
                        :attachments/content-type {:to "image/jpeg"}
                        :attachments/filename     {:to "image.jpg"}
                        :attachments/name         {:to "image.jpg"}}}
                     (->> c3
                          :notes/attachments
                          :added
                          (into #{} (map (fn [id] (get-in c3 [:attachments/changes id]))))))))

            (testing "returns the fourth change"
              (is (= {:notes/pinned? {:from true
                                      :to   false}}
                     (-> c4 (dissoc :notes/todos :todos/changes))))
              (is (= #{{:todos/completed? {:from false
                                           :to   true}
                        :todos/text       {:from "Do a thing"
                                           :to   "Did a thing"}}}
                     (->> c1
                          :notes/todos
                          :added
                          (into #{} (map (fn [id] (get-in c4 [:todos/changes id]))))))))

            (testing "returns the fifth change"
              (is (empty? (-> c5 (dissoc :notes/todos :todos/changes :attachments/changes))))
              (is (= #{{:todos/id         {:to t3}
                        :todos/text       {:to "Do some third thing"}
                        :todos/completed? {:to true}}}
                     (->> c5
                          :notes/todos
                          :added
                          (into #{} (map (fn [id] (get-in c5 [:todos/changes id])))))))
              (is (= #{{:attachments/name {:from "image.jpg"
                                           :to   "some other name"}}}
                     (->> c3
                          :notes/attachments
                          :added
                          (into #{} (map (fn [id] (get-in c5 [:attachments/changes id])))))))
              (is (= #{{:todos/completed? {:from false
                                           :to   true}}}
                     (->> c3
                          :notes/todos
                          :added
                          (into #{} (map (fn [id] (get-in c5 [:todos/changes id]))))))))

            (testing "returns the sixth change"
              (is (= {:notes/body    {:from "Some edited body goes here"
                                      :to   "Some completely different body"}
                      :notes/tags    {:removed #{:baz/quux} :added #{:other/tag}}
                      :notes/context {:from "Some context" :to "Some new context"}}
                     (-> c6 (dissoc :notes/todos :notes/attachments :todos/changes :attachments/changes))))
              (is (= (-> c6 :notes/todos :removed)
                     (-> c3 :notes/todos :added)))
              (is (= {:todos/id         {:from t2}
                      :todos/text       {:from "Do another thing"}
                      :todos/completed? {:from true}}
                     (->> c6 :notes/todos :removed first (get (:todos/changes c6)))))
              (is (= {:todos/text {:from "Do some third thing"
                                   :to   "Do some third thing still"}}
                     (->> c5 :notes/todos :added first (get (:todos/changes c6)))))
              (is (= (-> c6 :notes/attachments :removed)
                     (->> c2
                          :attachments/changes
                          (into #{} (comp (filter (comp #{a2} :to :attachments/id val))
                                          (map key))))))
              (is (= {:attachments/content-type {:from "application/pdf"}
                      :attachments/id           {:from a2}
                      :attachments/name         {:from "some-pdf.pdf"}
                      :attachments/filename     {:from "some-pdf.pdf"}}
                     (->> c6 :notes/attachments :removed first (get (:attachments/changes c6))))))

            (testing "returns the seventh change"
              (is (= {:notes/archived? {:from false :to true}}
                     c7)))

            (testing "returns the eighth change"
              (is (= {:notes/archived? {:from true :to false}}
                     c8)))

            (testing "has no more changes"
              (is (nil? c9)))))))))
