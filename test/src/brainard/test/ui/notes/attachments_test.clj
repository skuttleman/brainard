(ns brainard.test.ui.notes.attachments-test
  (:require
    [brainard.test.ui-system :as ui-sys]
    [brainard.test.ui.utils :as ui-utils]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest add-attachment-test
  (ui-sys/with-system [driver base-url]
    (let [fixture-path (-> "fixtures/sample.txt" io/resource .getPath)]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (eta/wait-visible driver {:css "h1.pinned-notes"})

        (testing "and when clicking the create note button"
          (ui-utils/click driver {:css "button.is-info"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when creating a note"
            (ui-utils/fill-form! driver ".modal-container.is-active form.form"
                                 {"Topic" "Test Attachments"
                                  "Body"  "Note with attachments"})

            (testing "and when uploading a file"
              (let [file-input (eta/query driver {:css ".modal-container.is-active input[type='file']"})]
                (eta/fill-el driver file-input fixture-path))
              (eta/wait-visible driver {:css ".attachment-list li"})

              (testing "displays the attachment in the list"
                (is (eta/has-text? driver {:css ".attachment-list"} "sample.txt")))

              (testing "and when submitting the form"
                (ui-utils/click driver {:css ".modal-container.is-active button.submit"})
                (eta/wait-invisible driver {:css ".modal-container.is-active"})

                (testing "creates the note"
                  (is (not (eta/exists? driver {:css ".modal-container.is-active"}))))

                (testing "and when navigating to the created note"
                  (eta/wait-visible driver {:css ".context-group[data-context='Test Attachments']"})
                  (ui-utils/click driver {:css ".context-group[data-context='Test Attachments'] .expand-context"})
                  (eta/wait-visible driver {:css "a[href*='/notes/']"})
                  (ui-utils/click driver {:css ".context-group[data-context='Test Attachments'] a[href*='/notes/']"})
                  (eta/wait-visible driver {:css ".attachment-list"})

                  (testing "displays the attachment"
                    (is (= 1 (count (eta/query-all driver {:css ".attachment-list li"}))))
                    (is (eta/has-text? driver {:css ".attachment-list"} "sample.txt")))

                  (testing "and when checking the attachment download link"
                    (let [attachment-link (eta/query driver {:xpath "//a[contains(text(), 'sample.txt')]"})]
                      (is (some? attachment-link))
                      (let [href (eta/get-element-attr-el driver attachment-link "href")]
                        (testing "link points to the attachment resource"
                          (is (re-find #"/attachments/" href))))))

                  (testing "and when clicking the edit button"
                    (ui-utils/click driver {:css "button.note__edit-button"})
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
                        (ui-utils/click driver {:css ".modal-container.is-active button.submit"})
                        (eta/wait-invisible driver {:css ".modal-container.is-active"})

                        (testing "updates the note"
                          (eta/wait-visible driver {:css ".attachment-list"})
                          (is (= 2 (count (eta/query-all driver {:css ".attachment-list li"})))))))))))))))))

(deftest edit-attachment-test
  (ui-sys/with-system [driver base-url]
    (let [fixture-path (-> "fixtures/sample.txt" io/resource .getPath)]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (eta/wait-visible driver {:css "h1.pinned-notes"})

        (testing "and when creating a note with an attachment"
          (ui-utils/click driver {:css "button.is-info"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})
          (ui-utils/fill-form! driver ".modal-container.is-active form.form"
                               {"Topic" "Edit Attachment Test"
                                "Body"  "Note for editing attachment"})
          (let [file-input (eta/query driver {:css ".modal-container.is-active input[type='file']"})]
            (eta/fill-el driver file-input fixture-path))
          (eta/wait-visible driver {:css ".attachment-list li"})
          (ui-utils/click driver {:css ".modal-container.is-active button.submit"})
          (eta/wait-invisible driver {:css ".modal-container.is-active"})

          (testing "and when navigating to the created note"
            (eta/wait-visible driver {:css ".context-group[data-context='Edit Attachment Test']"})
            (ui-utils/click driver {:css ".context-group[data-context='Edit Attachment Test'] .expand-context"})
            (eta/wait-visible driver {:css "a[href*='/notes/']"})
            (ui-utils/click driver {:css ".context-group[data-context='Edit Attachment Test'] a[href*='/notes/']"})
            (eta/wait-visible driver {:css ".attachment-list"})

            (testing "displays the attachment with default name"
              (is (eta/has-text? driver {:css ".attachment-list"} "sample.txt")))

            (testing "and when clicking the edit button"
              (ui-utils/click driver {:css "button.note__edit-button"})
              (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

              (testing "displays the attachment in edit form"
                (is (eta/has-text? driver {:css ".modal-container.is-active .attachment-list"} "sample.txt")))

              (testing "and when editing the attachment name"
                (ui-utils/click driver {:css "li.attachment i.lni-pencil"})
                (eta/wait-visible driver {:css ".modal-container.is-active .note-edit__attachment-name"})
                (ui-utils/fill-field! driver
                                      {:xpath "//*[@id=//label[text()='Attachment name']/@for]"}
                                      "renamed-attachment.txt")
                (ui-utils/click driver {:css ".note-edit__attachment-name button.submit"})
                (eta/wait-invisible driver {:css ".modal-container.is-active .note-edit__attachment-name"})

                (testing "updates the name in the form"
                  (is (eta/has-text? driver
                                     {:css ".modal-container.is-active .attachment-list"}
                                     "renamed-attachment.txt")))

                (testing "and when saving the note"
                  (ui-utils/click driver {:css ".modal-container.is-active button.submit"})
                  (eta/wait-invisible driver {:css ".modal-container.is-active"})

                  (testing "persists the attachment name change"
                    (eta/wait-visible driver {:css ".attachment-list"})
                    (is (eta/has-text? driver {:css ".attachment-list"} "renamed-attachment.txt"))))))))))))
