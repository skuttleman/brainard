(ns brainard.infra.store.specs-test
  (:require
    [brainard.infra.store.specs :as specs]
    [clojure.test :refer [deftest is testing]]
    [defacto.resources.core :as res]))

(defn ^:private modify-body [note-id spec]
  (get-in (res/->request-spec [::specs/notes#modify note-id] spec) [:params :body]))

(deftest notes-modify-diff-tags-test
  (testing "when prev-tags is nil"
    (is (nil? (:notes/tags (modify-body (random-uuid) {:payload {}})))))

  (testing "when tags are unchanged"
    (let [body (modify-body (random-uuid) {:payload   {:notes/tags #{:a :b}}
                                           :prev-tags #{:a :b}})]
      (is (= #{:a :b} (:notes/tags body)))
      (is (= #{} (:notes/tags!remove body)))))

  (testing "when tags are added"
    (let [body (modify-body (random-uuid) {:payload   {:notes/tags #{:a :b :c}}
                                           :prev-tags #{:a :b}})]
      (is (= #{:a :b :c} (:notes/tags body)))
      (is (= #{} (:notes/tags!remove body)))))

  (testing "when tags are removed"
    (let [body (modify-body (random-uuid) {:payload   {:notes/tags #{:b}}
                                           :prev-tags #{:a :b :c}})]
      (is (= #{:b} (:notes/tags body)))
      (is (= #{:a :c} (:notes/tags!remove body)))))

  (testing "when tags are added and removed"
    (let [body (modify-body (random-uuid) {:payload   {:notes/tags #{:b :d}}
                                           :prev-tags #{:a :b :c}})]
      (is (= #{:b :d} (:notes/tags body)))
      (is (= #{:a :c} (:notes/tags!remove body)))))

  (testing "when all tags are removed"
    (let [body (modify-body (random-uuid) {:payload   {:notes/tags #{}}
                                           :prev-tags #{:a :b}})]
      (is (= #{} (:notes/tags body)))
      (is (= #{:a :b} (:notes/tags!remove body))))))

(deftest notes-modify-diff-attachments-test
  (let [[a1 a2 a3] (repeatedly random-uuid)]
    (testing "when prev-attachments is nil"
      (is (nil? (:notes/attachments (modify-body (random-uuid) {:payload {}})))))

    (testing "when attachments are unchanged"
      (let [atts [{:attachments/id a1} {:attachments/id a2}]
            body (modify-body (random-uuid) {:payload          {:notes/attachments atts}
                                             :prev-attachments atts})]
        (is (= #{} (:notes/attachments!remove body)))
        (is (= (set atts) (:notes/attachments body)))))

    (testing "when an attachment is added"
      (let [body (modify-body (random-uuid) {:payload          {:notes/attachments [{:attachments/id a1}
                                                                                    {:attachments/id a2}]}
                                             :prev-attachments [{:attachments/id a1}]})]
        (is (= #{} (:notes/attachments!remove body)))
        (is (= #{{:attachments/id a1} {:attachments/id a2}} (:notes/attachments body)))))

    (testing "when an attachment is removed"
      (let [body (modify-body (random-uuid) {:payload          {:notes/attachments [{:attachments/id a2}]}
                                             :prev-attachments [{:attachments/id a1}
                                                                {:attachments/id a2}]})]
        (is (= #{a1} (:notes/attachments!remove body)))
        (is (= #{{:attachments/id a2}} (:notes/attachments body)))))

    (testing "when attachments are swapped"
      (let [body (modify-body (random-uuid) {:payload          {:notes/attachments [{:attachments/id a2}
                                                                                    {:attachments/id a3}]}
                                             :prev-attachments [{:attachments/id a1}
                                                                {:attachments/id a2}]})]
        (is (= #{a1} (:notes/attachments!remove body)))
        (is (= #{{:attachments/id a2} {:attachments/id a3}} (:notes/attachments body)))))))

(deftest notes-modify-diff-todos-test
  (let [[t1 t2 t3] (repeatedly random-uuid)]
    (testing "when prev-todos is nil"
      (is (nil? (:notes/todos (modify-body (random-uuid) {:payload {}})))))

    (testing "when a todo is added"
      (let [body (modify-body (random-uuid) {:payload    {:notes/todos [{:todos/id t1}
                                                                        {:todos/id t2}]}
                                             :prev-todos [{:todos/id t1}]})]
        (is (= #{} (:notes/todos!remove body)))
        (is (= #{{:todos/id t1} {:todos/id t2}} (:notes/todos body)))))

    (testing "when a todo is removed"
      (let [body (modify-body (random-uuid) {:payload    {:notes/todos [{:todos/id t2}]}
                                             :prev-todos [{:todos/id t1}
                                                          {:todos/id t2}]})]
        (is (= #{t1} (:notes/todos!remove body)))
        (is (= #{{:todos/id t2}} (:notes/todos body)))))

    (testing "when todos are swapped"
      (let [body (modify-body (random-uuid) {:payload    {:notes/todos [{:todos/id t2}
                                                                        {:todos/id t3}]}
                                             :prev-todos [{:todos/id t1}
                                                          {:todos/id t2}]})]
        (is (= #{t1} (:notes/todos!remove body)))
        (is (= #{{:todos/id t2} {:todos/id t3}} (:notes/todos body)))))))

(deftest notes-modify-scalar-fields-test
  (testing "only passes through allowed scalar fields"
    (let [note-id (random-uuid)
          body    (modify-body note-id {:payload {:notes/context "ctx"
                                                  :notes/pinned? true
                                                  :notes/body    "some body"
                                                  :notes/id      note-id
                                                  :notes/tags    #{:a}}})]
      (is (= "ctx" (:notes/context body)))
      (is (true? (:notes/pinned? body)))
      (is (= "some body" (:notes/body body)))
      (is (not (contains? body :notes/id))))))
