(ns brainard.test.ui.notes.core-test
  (:require
    [brainard.test.harness.ui.system :as usys]
    [brainard.test.harness.ui.utils :as tutils]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]
    [etaoin.keys :as keys]))

(deftest create-note-test
  (usys/with-webdriver [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (tutils/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when clicking the create note button"
        (tutils/click! driver {:css "button.note__create-button"})
        (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

        (testing "opens the create note modal"
          (is (eta/exists? driver {:css ".modal-container.is-active h1.note__modal-header"}))
          (is (= "Create note" (eta/get-element-text driver {:css ".modal-container.is-active h1.note__modal-header"}))))

        (testing "and when creating the note"
          (tutils/submit-form! driver
                               ".modal-container.is-active form.form"
                               {"Topic" "Test Context"
                                "Body"  "This is a test note created during UI testing"})
          (eta/wait-invisible driver {:css ".modal-container.is-active"})
          (eta/wait-absent driver {:css ".toast-message"})

          (testing "closes the modal"
            (is (not (eta/exists? driver {:css ".modal-container.is-active"}))))

          (testing "and when expanding the context group"
            (eta/wait-visible driver {:css ".context-group[data-context='Test Context']"})
            (tutils/click! driver {:css ".context-group[data-context='Test Context'] .expand-context"})

            (testing "displays the note"
              (is (eta/has-text? driver
                                 {:css ".context-group[data-context='Test Context']"}
                                 "This is a test note created during UI testing")))))))))

(deftest edit-note-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when clicking the edit button"
          (tutils/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "opens the edit note modal"
            (is (eta/exists? driver {:css ".modal-container.is-active h1.note__modal-header"}))
            (is (= "Edit note" (eta/get-element-text driver {:css ".modal-container.is-active h1.note__modal-header"}))))

          (testing "and when making a change"
            (let [textarea (eta/query driver {:css "textarea"})
                  current-body (eta/get-element-text-el driver textarea)]
              (eta/clear-el driver textarea)
              (eta/fill-el driver textarea (str current-body " [edited]")))

            (testing "and when cancelling the edit"
              (tutils/click! driver {:css ".modal-container.is-active button.cancel"})

              (testing "does not display the changes"
                (eta/wait-invisible driver {:css ".modal-container.is-active"})
                (is (not (eta/has-text? driver {:css ".content"} "[edited]"))))

              (testing "and when clicking the edit button"
                (tutils/click! driver {:css "button.note__edit-button"})
                (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

                (testing "opens the edit note modal"
                  (is (eta/exists? driver {:css ".modal-container.is-active h1.note__modal-header"}))
                  (is (= "Edit note" (eta/get-element-text driver {:css ".modal-container.is-active h1.note__modal-header"}))))

                (testing "and when making a change"
                  (let [textarea (eta/query driver {:css "textarea"})
                        current-body (eta/get-element-text-el driver textarea)]
                    (eta/clear-el driver textarea)
                    (eta/fill-el driver textarea (str current-body " [edited]")))

                  (testing "and when saving"
                    (tutils/click! driver {:css ".modal-container.is-active button.submit"})

                    (testing "displays the changes"
                      (eta/wait-invisible driver {:css ".modal-container.is-active"})
                      (is (eta/has-text? driver {:css ".content"} "[edited]")))))))))))))

(deftest delete-note-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (->> fix
                       first
                       :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "and when clicking the delete button"
          (tutils/click! driver {:css "button.is-danger"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})

          (testing "opens the delete confirmation modal"
            (is (eta/has-text? driver
                               {:css ".modal-container.is-active"}
                               "This note and all related schedules will be deleted")))

          (testing "and when confirming the delete"
            (tutils/click! driver {:css ".modal-container.is-active button.note__confirm-delete"})
            (eta/wait-invisible driver {:css ".modal-container.is-active"})

            (testing "redirects to home page"
              (eta/wait-visible driver {:css "h1.pinned-notes"})
              (is (contains? #{base-url (str base-url "/")}
                             (eta/get-url driver))))

            (testing "does not show the deleted note"
              (is (eta/has-text? driver {:css ".pinned-notes__section"} "No pinned notes")))

            (testing "and when visiting the deleted note"
              (eta/go driver (str base-url "/notes/" note-id))
              (eta/wait-visible driver {:css ".message.is-warning"})

              (testing "displays a warning message"
                (is (= "Note not found. Try creating one."
                       (eta/get-element-text driver {:css ".message.is-warning"})))))))))))

(deftest schedules-test
  (usys/with-webdriver [driver base-url {fix "buzz.edn"}]
    (let [note-sched-id (->> fix
                             (filter (comp #{"Note 1"} :notes/body))
                             first
                             :notes/id)
          note-no-sched-id (->> fix
                                (filter (comp #{"Note 2"} :notes/body))
                                first
                                :notes/id)]
      (testing "when visiting a note with an existing schedule"
        (eta/go driver (str base-url "/notes/" note-sched-id))
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "renders the existing schedule"
          (is (eta/exists? driver {:css "p.schedules__header"}))
          (is (not (eta/exists? driver {:css "p.schedules__empty"})))))

      (testing "when visiting a note with no schedules"
        (eta/go driver (str base-url "/notes/" note-no-sched-id))
        (eta/wait-visible driver {:css "form.schedule-form"})

        (testing "renders a notification"
          (is (eta/exists? driver {:css "p.schedules__empty"}))
          (is (= "no related schedules" (eta/get-element-text driver {:css "p.schedules__empty"}))))

        (testing "and when adding a schedule"
          (tutils/submit-form! driver "form.schedule-form" {"Day of the week" :monday})
          (eta/wait-invisible driver {:css "p.schedules__empty"})

          (testing "renders the schedule in the list"
            (is (eta/exists? driver {:css "p.schedules__header"}))
            (is (= "Existing schedules" (eta/get-element-text driver {:css "p.schedules__header"})))
            (is (eta/exists? driver {:xpath "//ul[contains(@class,'schedules__items')]//*[text()='monday']"})))

          (testing "and when deleting the schedule"
            (eta/wait-invisible driver {:css "div.message-body"})
            (tutils/click! driver {:css "button.schedules__delete"})
            (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
            (tutils/click! driver {:css ".modal-container.is-active button.delete-schedule"})

            (testing "removes the schedule from the list"
              (eta/wait-visible driver {:css "p.schedules__empty"})
              (is (not (eta/exists? driver {:css "p.schedules__header"}))))))))))

(deftest modal-closing-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when opening the note edit modal"
          (tutils/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when opening the todo edit modal"
            (tutils/click! driver {:css ".modal-item.note-edit__modal li.todo button:last-of-type"})
            (eta/wait-visible driver {:css ".modal-item.note-edit__todo"})

            (testing "and when pressing esc"
              (eta/fill-active driver keys/escape)
              (eta/wait-absent driver {:css ".modal-item.note-edit__todo"})

              (testing "closes the todo edit modal"
                (is (eta/absent? driver {:css ".modal-item.note-edit__todo"})))

              (testing "does not close the note edit modal"
                (is (eta/visible? driver {:css ".modal-container.is-active .modal-item.note-edit__modal"})))

              (testing "and when pressing esc again"
                (eta/fill-active driver keys/escape)
                (eta/wait-invisible driver {:css ".modal-container.is-active"})

                (testing "closes the note edit modal"
                  (is (not (eta/exists? driver {:css ".modal-container.is-active"}))))))))

        (testing "and when opening the note edit modal"
          (tutils/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when opening the todo edit modal"
            (tutils/click! driver {:css ".modal-item.note-edit__modal li.todo button:last-of-type"})
            (eta/wait-visible driver {:css ".modal-item.note-edit__todo"})

            (testing "and when clicking the modal-list"
              (tutils/js-events driver ".modal-list" [:click])
              (eta/wait-absent driver {:css ".modal-item.note-edit__todo"})

              (testing "closes the todo edit modal"
                (is (eta/absent? driver {:css ".modal-item.note-edit__todo"})))

              (testing "does not close the note edit modal"
                (is (eta/visible? driver {:css ".modal-container.is-active .modal-item.note-edit__modal"})))))

          (testing "and when opening the todo edit modal"
            (tutils/click! driver {:css ".modal-item.note-edit__modal li.todo button:last-of-type"})
            (eta/wait-visible driver {:css ".modal-item.note-edit__todo"})

            (testing "and when clicking the modal-stack"
              (tutils/js-events driver ".modal-stack" [:mousedown :mouseup])
              (eta/wait-invisible driver {:css ".modal-container.is-active"})

              (testing "closes all modals"
                (is (not (eta/exists? driver {:css ".modal-container.is-active"})))))))))))

(deftest tags-editor-test
  (usys/with-webdriver [driver base-url {fix "tags.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when opening the note edit modal"
          (tutils/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when editing the tags"
            (let [input-q {:css ".modal-container.is-active .tags-editor input.input"}
                  tag-list-q {:css ".modal-container.is-active .tag-list"}]

              (testing "and when typing a tag with no matches"
                (tutils/click! driver input-q)
                (tutils/fill-field! driver "Tags" "auniquetag")

                (testing "and when pressing enter"
                  (eta/fill-active driver keys/enter)

                  (testing "adds the tag to the list"
                    (tutils/wait-optimistic #(eta/has-text? driver tag-list-q ":auniquetag"))
                    (is (eta/has-text? driver tag-list-q ":auniquetag")))))

              (testing "and when typing a tag with matches"
                (tutils/click! driver input-q)
                (tutils/fill-field! driver "Tags" "ba")
                (eta/wait-visible driver {:css ".modal-container.is-active .type-ahead .dropdown.is-active"})

                (testing "and when pressing enter"
                  (eta/fill-active driver keys/enter)

                  (testing "does not add the tag to the list"
                    (tutils/wait-ambiguous)
                    (is (not (eta/has-text? driver tag-list-q ":ba"))))

                  (testing "and when pressing enter again"
                    (eta/fill-active driver keys/enter)

                    (testing "adds the tag to the list"
                      (tutils/wait-optimistic #(eta/has-text? driver tag-list-q ":ba"))
                      (is (eta/has-text? driver tag-list-q ":ba"))))))

              (testing "and when typing another tag with matches"
                (tutils/click! driver input-q)
                (tutils/fill-field! driver "Tags" "som")

                (testing "and when clicking one"
                  (eta/wait-visible driver {:css ".modal-container.is-active .type-ahead .dropdown.is-active"})
                  (tutils/click! driver {:xpath "//div[contains(@class,'type-ahead')]
                                                //li[contains(@class,'dropdown-item') and text()=':some/tag']"})

                  (testing "does not add the tag to the list"
                    (tutils/wait-ambiguous)
                    (is (not (eta/has-text? driver tag-list-q ":some/tag"))))

                  (testing "and when clicking add"
                    (tutils/click! driver {:css ".modal-container.is-active .tags-editor button.is-link"})

                    (testing "adds the tag to the list"
                      (tutils/wait-optimistic #(eta/has-text? driver tag-list-q ":some/tag"))
                      (is (eta/has-text? driver tag-list-q ":some/tag"))))))

              (testing "and when typing an invalid tag"
                (tutils/click! driver input-q)
                (tutils/fill-field! driver "Tags" "some/bad/input")

                (testing "and when pressing enter"
                  (eta/fill-active driver keys/enter)
                  (tutils/wait-optimistic #(eta/exists? driver {:css ".error-list"}))

                  (testing "displays an error"
                    (is (eta/exists? driver {:xpath "//*[contains(@class,'tags-editor')]
                                                     //*[contains(@class,'error-list')]
                                                     //*[text()='invalid tag']"})))

                  (testing "does not add the tag to the list"
                    (is (not (string/includes? (eta/get-element-text driver tag-list-q)
                                               "some/bad/input")))))))))))))
