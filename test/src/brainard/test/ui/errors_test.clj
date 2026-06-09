(ns brainard.test.ui.errors-test
  (:require
    [brainard.test.harness.ui.system :as usys]
    [brainard.test.harness.ui.web :as web]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest attachment-upload-error-test
  (usys/with-webdriver [driver base-url]
    (let [fixture-path (-> "fixtures/sample.txt" io/resource .getPath)]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "and when clicking the create note button"
          (web/click! driver {:css "button.note__create-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when uploading an attachment fails"
            (web/with-http-failure driver "Attachment test failures"
              (eta/fill driver {:css ".modal-container.is-active input[type='file']"} fixture-path)

              (testing "displays a toast message"
                (eta/wait-visible driver {:css ".toast-message.is-danger"})
                (is (eta/has-text? driver
                                   {:css ".toast-message.is-danger .body-text"}
                                   "Attachment test failures"))))))))))

(deftest completing-todo-error-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note with existing todos"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when activating todo fails"
          (web/with-http-failure driver "TODO test failure"
            (web/click! driver {:css "li.todo .checkbox"})

            (testing "displays a toast message"
              (eta/wait-visible driver {:css ".toast-message.is-danger"})
              (is (eta/has-text? driver
                                 {:css ".toast-message.is-danger .body-text"}
                                 "TODO test failure")))))))))

(deftest create-note-error-test
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

        (testing "and when creating the note fails"
          (web/with-http-failure driver "note creation test failure"
            (web/submit-form! driver
                              ".modal-container.is-active form.form"
                              {"Topic" "Test Context"
                               "Body"  "This is a test note created during UI testing"}))

          (testing "displays a toast message"
            (eta/wait-visible driver {:css ".toast-message.is-danger"})
            (is (eta/has-text? driver
                               {:css ".toast-message.is-danger .body-text"}
                               "note creation test failure")))

          (testing "does not close the modal"
            (eta/wait-absent driver {:css ".toast-message"})
            (is (eta/exists? driver {:css ".modal-container.is-active"}))))))))

(deftest delete-note-error-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (->> fix
                       first
                       :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when clicking the delete button"
          (web/click! driver {:css "button.is-danger"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})

          (testing "opens the delete confirmation modal"
            (is (eta/has-text? driver
                               {:css ".modal-container.is-active"}
                               "This note and all related schedules will be deleted")))

          (testing "and when confirming the delete fails"
            (web/with-http-failure driver "note deletion test failure"
              (web/click! driver {:css ".modal-container.is-active button.note__confirm-delete"})
              (eta/wait-invisible driver {:css ".modal-container.is-active"})

              (testing "displays a toast message"
                (eta/wait-visible driver {:css ".toast-message.is-danger"})
                (is (eta/has-text? driver
                                   {:css ".toast-message.is-danger .body-text"}
                                   "note deletion test failure"))))))))))

(deftest pin-note-error-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when unpinning the note fails"
          (web/with-http-failure driver "unpin test failure"
            (web/click! driver {:css ".note__toggle-pinned"})

            (testing "displays a toast message"
              (eta/wait-visible driver {:css ".toast-message.is-danger"})
              (is (eta/has-text? driver
                                 {:css ".toast-message.is-danger .body-text"}
                                 "unpin test failure")))))))))

(deftest reinstate-error-test
  (usys/with-webdriver [driver base-url {fix "history.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when viewing the note history"
          (web/click! driver {:css "button.note__history-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__modal"})

          (testing "and when showing version 3"
            (web/click! driver {:xpath "(//button[contains(@class,'note__history-show')])[3]"})
            (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})

            (testing "and when reinstating the note version fails"
              (web/with-http-failure driver "reinstatement test failure"
                (web/click! driver {:css ".history__view button.note__history-reinstate"})

                (testing "displays a toast message"
                  (eta/wait-visible driver {:css ".toast-message.is-danger"})
                  (is (eta/has-text? driver
                                     {:css ".toast-message.is-danger .body-text"}
                                     "reinstatement test failure")))))))))))

(deftest create-schedule-error-test
  (usys/with-webdriver [driver base-url {fix "base.edn"}]
    (let [note-id (->> fix first :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when adding a schedule fails"
          (web/with-http-failure driver "schedule test failure"
            (web/submit-form! driver "form.schedule-form" {"Day of the week" :monday})

            (testing "displays a toast message"
              (eta/wait-visible driver {:css ".toast-message.is-danger"})
              (is (eta/has-text? driver
                                 {:css ".toast-message.is-danger .body-text"}
                                 "schedule test failure")))))))))

(deftest delete-schedule-error-test
  (usys/with-webdriver [driver base-url {fix "buzz.edn"}]
    (let [note-id (->> fix
                       (filter (comp #{"Note 1"} :notes/body))
                       first
                       :notes/id)]
      (testing "when visiting the note"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when deleting the schedule fails"
          (web/click! driver {:css "button.schedules__delete"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
          (web/with-http-failure driver "delete schedule test failure"
            (web/click! driver {:css ".modal-container.is-active button.delete-schedule"})

            (testing "displays a toast message"
              (eta/wait-visible driver {:css ".toast-message.is-danger"})
              (is (eta/has-text? driver
                                 {:css ".toast-message.is-danger .body-text"}
                                 "delete schedule test failure")))))))))

(deftest create-workspace-error-test
  (usys/with-webdriver [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when creating a workspace node fails"
        (web/click! driver {:css ".drag-n-drop + .add-root-node"})
        (web/with-http-failure driver "workspace test failure"
          (web/submit-form! driver
                            ".modal-container.is-active form.form"
                            {"Content" "root node"})

          (testing "displays a toast message"
            (eta/wait-visible driver {:css ".toast-message.is-danger"})
            (is (eta/has-text? driver
                               {:css ".toast-message.is-danger .body-text"}
                               "workspace test failure"))))))))

(deftest delete-workspace-error-test
  (usys/with-webdriver [driver base-url {_ "workspace.edn"}]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when deleting a workspace node"
        (web/click! driver {:css ".drag-n-drop .lni-trash-can"})

        (testing "and when confirming the delete fails"
          (web/with-http-failure driver "workspace test failure"
            (web/click! driver {:css ".modal-container button.delete-node"})

            (testing "displays a toast message"
              (eta/wait-visible driver {:css ".toast-message.is-danger"})
              (is (eta/has-text? driver
                                 {:css ".toast-message.is-danger .body-text"}
                                 "workspace test failure")))))))))

(deftest modify-workspace-error-test
  (usys/with-webdriver [driver base-url {_ "workspace.edn"}]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when editing a workspace node fails"
        (web/click! driver {:css ".drag-n-drop .lni-pencil"})
        (eta/wait-visible driver {:css ".modal-container.is-active form.form"})
        (web/with-http-failure driver "workspace test failure"
          (web/submit-form! driver
                            ".modal-container.is-active form.form"
                            {"Content" "some new body"})

          (testing "displays a toast message"
            (eta/wait-visible driver {:css ".toast-message.is-danger"})
            (is (eta/has-text? driver
                               {:css ".toast-message.is-danger .body-text"}
                               "workspace test failure")))

          (testing "does not close the modal"
            (eta/wait-absent driver {:css ".toast-message"})
            (is (eta/exists? driver {:css ".modal-container.is-active"}))))))))

(deftest small-screen-test
  (usys/with-webdriver [driver base-url]
    (testing "when the screen is too small"
      (eta/set-window-size driver {:height 500 :width 1000})

      (doseq [path ["/" "/search" "/foo"]]
        (testing (str "and when visiting: " path)
          (eta/go driver (str base-url path))
          (eta/wait-visible driver {:css "#unavailable h1.title"})

          (testing "displays alert"
            (is (eta/invisible? driver {:css "#root"}))
            (is (eta/visible? driver {:css "#unavailable"}))

            (is (= ["This app cannot be rendered on screens/windows this small."
                    "Please increase window size or view on a different device."]
                   (map (partial eta/get-element-text-el driver)
                        (eta/query-all driver {:css "#unavailable .message.is-warning p"}))))))))))
