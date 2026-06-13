(ns brainard.test.ui.notes.core-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [etaoin.api :as eta]
   [etaoin.keys :as keys]))

(deftest create-note-test
  (usys/with-webdriver [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when clicking the create note button"
        (web/click! driver {:css "button.note__create-button"})
        (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

        (testing "opens the create note modal"
          (is (eta/exists? driver {:css ".modal-container.is-active h1.note__modal-header"}))
          (is (= "Create note" (eta/get-element-text driver {:css ".modal-container.is-active h1.note__modal-header"}))))

        (testing "and when creating the note"
          (web/submit-form! driver
                            ".modal-container.is-active form.form"
                            {"Topic" "Test Context"
                             "Body"  "This is a test note created during UI testing"})
          (eta/wait-invisible driver {:css ".modal-container.is-active"})
          (eta/wait-visible driver {:css ".toast-message.is-success"})

          (let [note (eta/query driver {:css ".toast-message.is-success .body-text"})
                note-link (eta/query-from-shadow-root-el driver note {:css "a"})
                href (eta/get-element-attr-el driver note-link "href")]
            (testing "displays a toast message"
              (is (= "new note" (eta/get-element-text-el driver note-link)))
              (is (= "a new note was created"
                     (string/replace (eta/get-element-text-el driver note) #"\s+" " "))))

            (testing "points to the note resource"
              (is (re-find #"/notes/" href))))

          (testing "closes the modal"
            (eta/wait-absent driver {:css ".toast-message"})
            (is (not (eta/exists? driver {:css ".modal-container.is-active"}))))

          (testing "and when expanding the context group"
            (eta/wait-visible driver {:css ".context-group[data-context='Test Context']"})
            (web/click! driver {:css ".context-group[data-context='Test Context'] .expand-context"})

            (testing "displays the note"
              (is (eta/has-text? driver
                                 {:css ".context-group[data-context='Test Context']"}
                                 "This is a test note created during UI testing")))))))))

(deftest edit-note-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when clicking the edit button"
          (web/click! driver {:css "button.note__edit-button"})
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
              (web/click! driver {:css ".modal-container.is-active button.cancel"})

              (testing "does not display the changes"
                (eta/wait-invisible driver {:css ".modal-container.is-active"})
                (is (not (eta/has-text? driver {:css ".content"} "[edited]"))))

              (testing "and when clicking the edit button"
                (web/click! driver {:css "button.note__edit-button"})
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
                    (web/click! driver {:css ".modal-container.is-active button.submit"})

                    (testing "displays the changes"
                      (eta/wait-invisible driver {:css ".modal-container.is-active"})
                      (is (eta/has-text? driver {:css ".content"} "[edited]")))))))))))))

(deftest archive-note-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (->> fix
                       first
                       :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when clicking the archive button"
          (web/click! driver {:css "button.note__archive-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})

          (testing "opens the archive confirmation modal"
            (is (eta/has-text? driver
                               {:css ".modal-container.is-active"}
                               "This note will be archived. Archived notes are deleted after 30 days.")))

          (testing "and when confirming the archival"
            (web/click! driver {:css ".modal-container.is-active button.note__confirm-archive"})
            (eta/wait-invisible driver {:css ".modal-container.is-active"})

            (testing "displays a toast message"
              (eta/wait-visible driver {:css ".toast-message.is-success"})
              (is (eta/has-text? driver
                                 {:css ".toast-message.is-success .body-text"}
                                 "note archived")))

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
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

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
          (web/submit-form! driver "form.schedule-form" {"Day of the week" :monday})
          (eta/wait-invisible driver {:css "p.schedules__empty"})

          (testing "displays a toast message"
            (eta/wait-visible driver {:css ".toast-message.is-success"})
            (is (eta/has-text? driver
                               {:css ".toast-message.is-success .body-text"}
                               "schedule created"))
            (eta/wait-absent driver {:css ".toast-message"}))

          (testing "renders the schedule in the list"
            (is (eta/exists? driver {:css "p.schedules__header"}))
            (is (= "Existing schedules" (eta/get-element-text driver {:css "p.schedules__header"})))
            (is (eta/exists? driver {:xpath "//ul[contains(@class,'schedules__items')]//*[text()='monday']"})))

          (testing "and when deleting the schedule"
            (eta/wait-invisible driver {:css "div.message-body"})
            (web/click! driver {:css "button.schedules__delete"})
            (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
            (web/click! driver {:css ".modal-container.is-active button.delete-schedule"})

            (testing "displays a toast message"
              (eta/wait-visible driver {:css ".toast-message.is-success"})
              (is (eta/has-text? driver
                                 {:css ".toast-message.is-success .body-text"}
                                 "schedule deleted"))
              (eta/wait-absent driver {:css ".toast-message"}))

            (testing "removes the schedule from the list"
              (eta/wait-visible driver {:css "p.schedules__empty"})
              (is (not (eta/exists? driver {:css "p.schedules__header"}))))))))))

(deftest modal-closing-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when opening the note edit modal"
          (web/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when opening the todo edit modal"
            (web/click! driver {:css ".modal-item.note-edit__modal li.todo button:last-of-type"})
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
          (web/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when opening the todo edit modal"
            (web/click! driver {:css ".modal-item.note-edit__modal li.todo button:last-of-type"})
            (eta/wait-visible driver {:css ".modal-item.note-edit__todo"})

            (testing "and when clicking the modal-list"
              (web/js-events driver ".modal-list" [:click])
              (eta/wait-absent driver {:css ".modal-item.note-edit__todo"})

              (testing "closes the todo edit modal"
                (is (eta/absent? driver {:css ".modal-item.note-edit__todo"})))

              (testing "does not close the note edit modal"
                (is (eta/visible? driver {:css ".modal-container.is-active .modal-item.note-edit__modal"})))))

          (testing "and when opening the todo edit modal"
            (web/click! driver {:css ".modal-item.note-edit__modal li.todo button:last-of-type"})
            (eta/wait-visible driver {:css ".modal-item.note-edit__todo"})

            (testing "and when clicking the modal-stack"
              (web/js-events driver ".modal-stack" [:mousedown :mouseup])
              (eta/wait-invisible driver {:css ".modal-container.is-active"})

              (testing "closes all modals"
                (is (not (eta/exists? driver {:css ".modal-container.is-active"})))))))))))

(deftest tags-editor-test
  (usys/with-webdriver [driver base-url {fix "tags.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when opening the note edit modal"
          (web/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when editing the tags"
            (let [input-q {:css ".modal-container.is-active .tags-editor input.input"}
                  tag-list-q {:css ".modal-container.is-active .tag-list"}]

              (testing "and when typing a tag with no matches"
                (web/click! driver input-q)
                (web/fill-field! driver "Tags" "auniquetag")

                (testing "and when pressing enter"
                  (eta/fill-active driver keys/enter)

                  (testing "adds the tag to the list"
                    (web/wait-optimistic #(eta/has-text? driver tag-list-q ":auniquetag"))
                    (is (eta/has-text? driver tag-list-q ":auniquetag")))))

              (testing "and when typing a tag with matches"
                (web/click! driver input-q)
                (web/fill-field! driver "Tags" "ba")
                (eta/wait-visible driver {:css ".modal-container.is-active .type-ahead .dropdown.is-active"})

                (testing "and when pressing enter"
                  (eta/fill-active driver keys/enter)

                  (testing "does not add the tag to the list"
                    (web/wait-ambiguous)
                    (is (not (eta/has-text? driver tag-list-q ":ba"))))

                  (testing "and when pressing enter again"
                    (eta/fill-active driver keys/enter)

                    (testing "adds the tag to the list"
                      (web/wait-optimistic #(eta/has-text? driver tag-list-q ":ba"))
                      (is (eta/has-text? driver tag-list-q ":ba"))))))

              (testing "and when typing another tag with matches"
                (web/click! driver input-q)
                (web/fill-field! driver "Tags" "som")

                (testing "and when clicking one"
                  (eta/wait-visible driver {:css ".modal-container.is-active .type-ahead .dropdown.is-active"})
                  (web/click! driver {:xpath "//div[contains(@class,'type-ahead')]
                                                //li[contains(@class,'dropdown-item') and text()=':some/tag']"})

                  (testing "does not add the tag to the list"
                    (web/wait-ambiguous)
                    (is (not (eta/has-text? driver tag-list-q ":some/tag"))))

                  (testing "and when clicking add"
                    (web/click! driver {:css ".modal-container.is-active .tags-editor button.is-link"})

                    (testing "adds the tag to the list"
                      (web/wait-optimistic #(eta/has-text? driver tag-list-q ":some/tag"))
                      (is (eta/has-text? driver tag-list-q ":some/tag"))))))

              (testing "and when typing an invalid tag"
                (web/click! driver input-q)
                (web/fill-field! driver "Tags" "some/bad/input")

                (testing "and when pressing enter"
                  (eta/fill-active driver keys/enter)
                  (web/wait-optimistic #(eta/exists? driver {:css ".error-list"}))

                  (testing "displays an error"
                    (is (eta/exists? driver {:xpath "//*[contains(@class,'tags-editor')]
                                                     //*[contains(@class,'error-list')]
                                                     //*[text()='invalid tag']"})))

                  (testing "does not add the tag to the list"
                    (is (not (string/includes? (eta/get-element-text driver tag-list-q)
                                               "some/bad/input")))))))))))))
