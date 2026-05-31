(ns brainard.test.ui.notes.attachments-test
  (:require
    [brainard.test.harness.ui.system :as usys]
    [brainard.test.harness.ui.utils :as tutils]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest add-attachment-test
  (usys/with-webdriver [driver base-url]
    (let [fixture-path (-> "fixtures/sample.txt" io/resource .getPath)]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "and when clicking the create note button"
          (tutils/click! driver {:css "button.note__create-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when creating a note"
            (tutils/fill-form! driver ".modal-container.is-active form.form"
                               {"Topic" "Test Attachments"
                                "Body"  "Note with attachments"})

            (testing "and when uploading a file"
              (let [file-input (eta/query driver {:css ".modal-container.is-active input[type='file']"})]
                (eta/fill-el driver file-input fixture-path))
              (eta/wait-visible driver {:css ".attachment-list li"})

              (testing "displays the attachment in the list"
                (is (eta/has-text? driver {:css ".attachment-list"} "sample.txt")))

              (testing "and when submitting the form"
                (tutils/click! driver {:css ".modal-container.is-active button.submit"})
                (eta/wait-invisible driver {:css ".modal-container.is-active"})
                (eta/wait-absent driver {:css ".toast-message"})

                (testing "creates the note"
                  (is (not (eta/exists? driver {:css ".modal-container.is-active"}))))

                (testing "and when navigating to the created note"
                  (eta/wait-visible driver {:css ".context-group[data-context='Test Attachments']"})
                  (tutils/click! driver {:css ".context-group[data-context='Test Attachments'] .expand-context"})
                  (eta/wait-visible driver {:css "a[href*='/notes/']"})
                  (tutils/click! driver {:css ".context-group[data-context='Test Attachments'] a[href*='/notes/']"})
                  (eta/wait-visible driver {:css ".attachment-list"})
                  (eta/wait-absent driver {:css ".toast-message"})

                  (testing "displays the attachment"
                    (is (= 1 (count (eta/query-all driver {:css ".attachment-list li"}))))
                    (is (eta/has-text? driver {:css ".attachment-list"} "sample.txt")))

                  (testing "and when checking the attachment download link"
                    (let [attachment-link (eta/query driver {:xpath "//a[contains(text(), 'sample.txt')]"})]
                      (is (some? attachment-link))
                      (let [href (eta/get-element-attr-el driver attachment-link "href")]
                        (testing "points to the attachment resource"
                          (is (re-find #"/attachments/" href))))))

                  (testing "and when clicking the edit button"
                    (tutils/click! driver {:css "button.note__edit-button"})
                    (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

                    (testing "opens the edit note modal"
                      (is (= "Edit note"
                             (eta/get-element-text driver {:css ".modal-container.is-active h1.note__modal-header"}))))

                    (testing "displays the existing attachment"
                      (is (eta/has-text? driver {:css ".modal-container.is-active .attachment-list"} "sample.txt")))

                    (testing "and when adding another attachment"
                      (let [file-input (eta/query driver {:css ".modal-container.is-active input[type='file']"})]
                        (eta/fill-el driver file-input fixture-path))
                      (eta/wait-visible driver {:css "ul.attachment-list li + li"})

                      (testing "displays both attachments"
                        (is (= 2 (count (eta/query-all driver
                                                       {:css ".modal-container.is-active .attachment-list li"})))))

                      (testing "and when saving the changes"
                        (tutils/click! driver {:css ".modal-container.is-active button.submit"})
                        (eta/wait-invisible driver {:css ".modal-container.is-active"})

                        (testing "updates the note"
                          (eta/wait-visible driver {:css ".attachment-list"})
                          (is (= 2 (count (eta/query-all driver {:css ".attachment-list li"})))))

                        (testing "and when downloading the attachment"
                          (tutils/click! driver {:css ".attachment-list li a"})
                          (tutils/wait-optimistic #(= (count (eta/get-window-handles driver)) 2))
                          (eta/switch-window-next driver)
                          (is (= "some text\ngoes here." (eta/get-element-text driver {:css "body"}))))))))))))))))

(deftest large-attachment-test
  (usys/with-webdriver [driver base-url]
    (let [fixture-path (-> "fixtures/large.txt" io/resource .getPath)]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "and when clicking the create note button"
          (tutils/click! driver {:css "button.note__create-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when creating a note"
            (tutils/fill-form! driver ".modal-container.is-active form.form"
                               {"Topic" "Test Attachments"
                                "Body"  "Note with attachments"})

            (testing "and when uploading a file"
              (let [file-input (eta/query driver {:css ".modal-container.is-active input[type='file']"})]
                (eta/fill-el driver file-input fixture-path)
                (eta/wait-visible driver {:css "li.toast-message.message"})
                (testing "displays an error"
                  (is (eta/has-text? driver
                                     {:css "li.toast-message.message .body-text"}
                                     "A file being uploaded exceeds the maximum allowed size")))

                (testing "does not display the attachment in the list"
                  (is (eta/absent? driver {:css ".modal-container .attachment-list li"})))))))))))

(deftest edit-attachment-test
  (usys/with-webdriver [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (tutils/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when creating a note with an attachment"
        (tutils/click! driver {:css "button.note__create-button"})
        (eta/wait-visible driver {:css ".modal-container.is-active form.form"})
        (tutils/fill-form! driver ".modal-container.is-active form.form"
                           {"Topic" "Edit Attachment Test"
                            "Body"  "Note for editing attachment"})
        (eta/fill driver
                  {:css ".modal-container.is-active input[type='file']"}
                  (-> "fixtures/sample.txt" io/resource .getPath))
        (eta/wait-visible driver {:css ".attachment-list li"})
        (tutils/click! driver {:css ".modal-container.is-active button.submit"})
        (eta/wait-invisible driver {:css ".modal-container.is-active"})
        (eta/wait-absent driver {:css ".toast-message"})

        (testing "and when navigating to the created note"
          (eta/wait-visible driver {:css ".context-group[data-context='Edit Attachment Test']"})
          (tutils/click! driver {:css ".context-group[data-context='Edit Attachment Test'] .expand-context"})
          (eta/wait-visible driver {:css "a[href*='/notes/']"})
          (tutils/click! driver {:css ".context-group[data-context='Edit Attachment Test'] a[href*='/notes/']"})
          (eta/wait-visible driver {:css ".attachment-list"})
          (eta/wait-absent driver {:css ".toast-message"})

          (testing "displays the attachment with default name"
            (is (eta/has-text? driver {:css ".attachment-list"} "sample.txt")))

          (testing "and when clicking the edit button"
            (tutils/click! driver {:css "button.note__edit-button"})
            (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

            (testing "displays the attachment in edit form"
              (is (eta/has-text? driver {:css ".modal-container.is-active .attachment-list"} "sample.txt")))

            (testing "and when editing the attachment name"
              (tutils/click! driver {:css "li.attachment i.lni-pencil"})
              (eta/wait-visible driver {:css ".modal-container.is-active .note-edit__attachment-name"})
              (tutils/fill-field! driver "Attachment name" "renamed-attachment.txt")
              (tutils/click! driver {:css ".note-edit__attachment-name button.submit"})
              (eta/wait-invisible driver {:css ".modal-container.is-active .note-edit__attachment-name"})
              (eta/wait-absent driver {:css ".toast-message"})

              (testing "updates the name in the form"
                (is (eta/has-text? driver
                                   {:css ".modal-container.is-active .attachment-list"}
                                   "renamed-attachment.txt")))

              (testing "and when saving the note"
                (tutils/click! driver {:css ".modal-container.is-active button.submit"})
                (eta/wait-invisible driver {:css ".modal-container.is-active"})

                (testing "persists the attachment name change"
                  (eta/wait-visible driver {:css ".attachment-list"})
                  (is (eta/has-text? driver {:css ".attachment-list"} "renamed-attachment.txt")))))))))))

(deftest remove-attachment-test
  (usys/with-webdriver [driver base-url]
    (let [fixture-path (-> "fixtures/sample.txt" io/resource .getPath)]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (tutils/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "and when creating a note with two attachments"
          (tutils/click! driver {:css "button.note__create-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})
          (tutils/fill-form! driver ".modal-container.is-active form.form"
                             {"Topic" "Remove Attachment Test"
                              "Body"  "Note with multiple attachments"})
          (let [file-input (eta/query driver {:css ".modal-container.is-active input[type='file']"})]
            (eta/fill-el driver file-input fixture-path))
          (eta/wait-visible driver {:css "ul.attachment-list li"})
          (let [file-input (eta/query driver {:css ".modal-container.is-active input[type='file']"})]
            (eta/fill-el driver file-input fixture-path))
          (eta/wait-visible driver {:css "ul.attachment-list li + li"})

          (testing "and when saving the note"
            (tutils/click! driver {:css ".modal-container.is-active button.submit"})
            (eta/wait-invisible driver {:css ".modal-container.is-active"})

            (testing "and when navigating to the created note"
              (eta/wait-visible driver {:css ".context-group[data-context='Remove Attachment Test']"})
              (tutils/click! driver {:css ".context-group[data-context='Remove Attachment Test'] .expand-context"})
              (eta/wait-visible driver {:css "a[href*='/notes/']"})
              (tutils/click! driver {:css ".context-group[data-context='Remove Attachment Test'] a[href*='/notes/']"})
              (eta/wait-visible driver {:css ".attachment-list"})

              (testing "displays both attachments"
                (is (= 2 (count (eta/query-all driver {:css ".attachment-list li"})))))

              (testing "and when clicking the edit button"
                (tutils/click! driver {:css "button.note__edit-button"})
                (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

                (testing "and when removing an attachment"
                  (tutils/click! driver {:css "li.attachment i.lni-trash-can"})
                  (testing "removes the attachment from the form"
                    (is (= 1 (count (eta/query-all driver {:css ".modal-container.is-active .attachment-list li"})))))

                  (testing "and when saving the note"
                    (tutils/click! driver {:css ".modal-container.is-active button.submit"})
                    (eta/wait-invisible driver {:css ".modal-container.is-active"}))

                  (testing "persists the removal"
                    (eta/wait-visible driver {:css ".attachment-list"})
                    (is (= 1 (count (eta/query-all driver {:css ".attachment-list li"}))))))))))))))
