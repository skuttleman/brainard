(ns brainard.test.ui.notes.core-test
  (:require
    [brainard.test.harness.ui.system :as usys]
    [brainard.test.harness.ui.utils :as tutils]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest create-note-test
  (usys/with-webdriver [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (eta/wait-visible driver {:css "h1.pinned-notes"})

      (testing "and when clicking the create note button"
        (tutils/click driver {:css "button.note__create-button"})
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

          (testing "closes the modal"
            (is (not (eta/exists? driver {:css ".modal-container.is-active"}))))

          (testing "and when expanding the context group"
            (eta/wait-visible driver {:css ".context-group[data-context='Test Context']"})
            (tutils/click driver {:css ".context-group[data-context='Test Context'] .expand-context"})

            (testing "displays the note"
              (is (eta/has-text? driver
                                 {:css ".context-group[data-context='Test Context']"}
                                 "This is a test note created during UI testing")))))))))

(deftest edit-note-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when clicking the edit button"
          (tutils/click driver {:css "button.note__edit-button"})
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
              (tutils/click driver {:css ".modal-container.is-active button.cancel"})

              (testing "does not display the changes"
                (eta/wait-invisible driver {:css ".modal-container.is-active"})
                (is (not (eta/has-text? driver {:css ".content"} "[edited]"))))

              (testing "and when clicking the edit button"
                (tutils/click driver {:css "button.note__edit-button"})
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
                    (tutils/click driver {:css ".modal-container.is-active button.submit"})

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
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when clicking the delete button"
          (tutils/click driver {:css "button.is-danger"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})

          (testing "opens the delete confirmation modal"
            (is (eta/has-text? driver
                               {:css ".modal-container.is-active"}
                               "This note and all related schedules will be deleted")))

          (testing "and when confirming the delete"
            (tutils/click driver {:css ".modal-container.is-active button.note__confirm-delete"})
            (eta/wait-invisible driver {:css ".modal-container.is-active"})

            (testing "redirects to home page"
              (eta/wait-visible driver {:css "h1.pinned-notes"})
              (is (contains? #{base-url (str base-url "/")}
                             (eta/get-url driver))))

            (testing "does not show the deleted note"
              (is (eta/has-text? driver {:css ".pinned-notes__section"} "No pinned notes")))

            (testing "and when visiting the deleted note"
              (eta/go driver (str base-url "/notes/" note-id))

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
        (eta/wait-visible driver {:css "form.schedule-form"})

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
            (tutils/click driver {:css "button.schedules__delete"})
            (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
            (tutils/click driver {:css ".modal-container.is-active button.delete-schedule"})

            (testing "removes the schedule from the list"
              (eta/wait-visible driver {:css "p.schedules__empty"})
              (is (not (eta/exists? driver {:css "p.schedules__header"}))))))))))
