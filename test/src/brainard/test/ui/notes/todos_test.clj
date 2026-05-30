(ns brainard.test.ui.notes.todos-test
  (:require
    [brainard.test.harness.ui.system :as usys]
    [brainard.test.harness.ui.utils :as tutils]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest add-todo-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when clicking the edit button"
          (tutils/click driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active h1.note__modal-header"})

          (testing "and when clicking the create todo button"
            (tutils/click driver {:css "button.note__create-todo-button"})
            (eta/wait-visible driver {:css ".modal-container.is-active .note-edit__todo"})

            (testing "opens the create todo modal"
              (is (eta/has-text? driver
                                 {:css ".modal-container.is-active .note-edit__todo"}
                                 "Create new TODO")))

            (testing "and when creating a todo"
              (eta/wait-visible driver {:css ".modal-container.is-active input"})
              (tutils/fill-field! driver "TODO" "New test todo")
              (eta/wait-visible driver {:css ".modal-container.is-active button.submit"})
              (eta/click driver {:css ".note-edit__todo button.submit"})

              (testing "closes the todo modal and displays the new todo"
                (eta/wait-invisible driver {:css ".modal-container.is-active .note-edit__todo"})
                (eta/wait-visible driver {:css ".modal-container.is-active ul.todo-list li + li"})
                (is (eta/has-text? driver
                                   {:css ".modal-container.is-active ul.todo-list"}
                                   "New test todo"))))))))))

(deftest edit-todo-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note with existing todos"
        (eta/go driver (str base-url "/notes/" note-id))
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when clicking the edit button"
          (tutils/click driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active ul.todo-list"})

          (testing "and when clicking the edit icon on a todo"
            (tutils/click driver {:css ".modal-container.is-active i.lni-pencil"})
            (eta/wait-visible driver {:css ".modal-container.is-active .note-edit__todo"})

            (testing "opens the edit todo modal"
              (is (eta/has-text? driver
                                 {:css ".modal-container.is-active .note-edit__todo"}
                                 "Edit your TODO")))

            (testing "and when modifying and saving the todo"
              (tutils/fill-field! driver "TODO" "Updated todo text")
              (tutils/click driver {:css ".note-edit__todo button.submit"})
              (eta/wait-invisible driver {:css ".modal-container.is-active .note-edit__todo"})

              (testing "closes the todo modal and displays the updated todo"
                (is (eta/has-text? driver
                                   {:css ".modal-container.is-active ul.todo-list"}
                                   "Updated todo text"))))))))))

(deftest complete-todo-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note with existing todos"
        (eta/go driver (str base-url "/notes/" note-id))
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when deselecting the todo"
          (tutils/click driver {:css "li.todo .checkbox"})
          (tutils/wait-optimistic #(and (eta/exists? driver {:css "li.todo .checkbox"})
                                        (not (eta/get-element-attr driver {:css "li.todo .checkbox"} "checked"))))
          (testing "marks the todo as active"
            (eta/refresh driver)
            (eta/wait-visible driver {:css "h1.layout--space-after"})
            (is (not (eta/get-element-attr driver {:css "li.todo .checkbox"} "checked"))))

          (testing "and when selecting the todo"
            (tutils/click driver {:css "li.todo .checkbox"})
            (tutils/wait-optimistic #(and (eta/exists? driver {:css "li.todo .checkbox"})
                                          (eta/get-element-attr driver {:css "li.todo .checkbox"} "checked")))
            (testing "marks the todo as active"
              (eta/refresh driver)
              (eta/wait-visible driver {:css "h1.layout--space-after"})
              (is (eta/get-element-attr driver {:css "li.todo .checkbox"} "checked")))))))))

(deftest delete-todo-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note with existing todos"
        (eta/go driver (str base-url "/notes/" note-id))
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when clicking the edit button"
          (tutils/click driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when clicking the delete icon on a todo"
            (let [todos-before (count (eta/query-all driver {:css ".modal-container.is-active ul.todo-list li.todo"}))]
              (tutils/click driver {:css ".modal-container.is-active i.lni-trash-can"})

              (testing "and when saving the note"
                (tutils/click driver {:css ".note-edit__modal button.submit"})
                (eta/wait-invisible driver {:css ".modal-container.is-active"}))

              (testing "removes the todo from the list"
                (let [todos-after (count (eta/query-all driver {:css "ul.todo-list li.todo"}))]
                  (is (= (dec todos-before) todos-after)))))))))))
