(ns brainard.test.ui.notes.keyboard-shortcuts-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.test :refer [deftest is testing]]
   [etaoin.api :as eta]
   [etaoin.keys :as ek]))

(deftest note-modal-test
  (usys/with-webdriver [driver base-url {_ "search.edn"}]
    (testing "when visiting any page"
      (let [[page path] (rand-nth [["home" nil] ["search" "/search"] ["buzz" "/buzz"] ["trash" "/bin"]])]
        (eta/go driver (str base-url path))
        (web/wait-optimistic #(eta/visible? driver {:css (str ".page__" page)})))

      (testing "and when creating a new note"
        (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left "n"))
        (eta/wait-visible driver {:css ".modal-container.is-active form.form"})
        (web/fill-form! driver
                        ".modal-container.is-active form.form"
                        {"Topic" "Test Context"
                         "Body"  "This is a test note created during UI testing"})

        (testing "and when adding a todo"
          (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left "t"))
          (web/wait-optimistic #(eta/visible? driver {:css ".note-edit__todo"}))
          (is (eta/visible? driver {:css ".note-edit__todo"}))
          (eta/fill-active driver ek/escape)
          (eta/wait-absent driver {:css ".note-edit__todo"}))

        (testing "and when adding a link"
          (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left "l"))
          (web/wait-optimistic #(eta/visible? driver {:css ".note-edit__link"}))
          (is (eta/visible? driver {:css ".note-edit__link"}))
          (eta/fill-active driver ek/escape)
          (eta/wait-absent driver {:css ".note-edit__link"}))

        (testing "and when adding an attachment"
          (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left "a"))
          (web/wait-optimistic #(false? (eta/js-execute driver "return document.hasFocus()")))

          (testing "loses focus"
            (is (false? (eta/js-execute driver "return document.hasFocus()")))))))))

(deftest archive-note-modal-test
  (usys/with-webdriver [driver base-url {fix "search.edn"}]
    (let [note-id (->> fix
                       (filter (comp #{"Note one C"} :notes/body))
                       first
                       :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when archiving the note"
          (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left "d"))
          (web/wait-optimistic #(eta/visible? driver {:css ".modal-container.is-active"}))

          (testing "renders the confirmation modal"
            (is (eta/exists? driver {:css ".modal-container .note__confirm-archive"}))))))))
