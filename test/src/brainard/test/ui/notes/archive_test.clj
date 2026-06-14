(ns brainard.test.ui.notes.archive-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.test :refer [deftest is testing]]
   [etaoin.api :as eta]))

(defn ^:private note-visible? [driver body]
  (let [xpath (format "//span[contains(@class,'truncate') and text()='%s']" body)]
    (eta/visible? driver {:xpath xpath})))

(deftest empty-recycle-bin-test
  (usys/with-webdriver [driver base-url {_ "archived.edn"}]
      (testing "when visiting the recycle bin"
        (eta/go driver (str base-url "/bin"))
        (eta/wait-visible driver {:css ".page__trash"})

        (testing "displays the archived notes"
          (is (note-visible? driver "Some body 1"))
          (is (note-visible? driver "Some body 2"))
          (is (not (note-visible? driver "Some body 3"))))

        (testing "and when emptying the recycle bin"
          (web/click! driver {:css ".note__empty-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active"})
          (web/click! driver {:css ".note__confirm-empty"})
          (eta/wait-absent driver {:css ".modal-container.is-active"})
          (eta/wait-visible driver {:css "span.search-results"})

          (testing "renders no results"
            (is (eta/has-text? driver
                               {:css ".search-results .message-body"}
                               "No search results")))

          (testing "does not render the empty button"
            (is (not (eta/exists? driver {:css ".note__empty-button"}))))))))

(deftest restore-test
  (usys/with-webdriver [driver base-url {fix "archived.edn"}]
    (testing "when visiting the recycle bin"
      (eta/go driver (str base-url "/bin"))
      (eta/wait-visible driver {:css ".page__trash"})

      (testing "and when restoring a note"
        (let [note-id (-> fix second :notes/id)]
          (web/click! driver {:xpath (format "//li[@id='%s']
                                              //button[contains(@class,'note__restore-button')]"
                                             note-id)})
          (eta/wait-visible driver {:css ".page__note"})

          (testing "redirects to the note page"
            (is (= (str base-url "/notes/" note-id) (eta/get-url driver)))
            (eta/wait-visible driver {:css ".content"})
            (is (eta/has-text? driver {:css ".content"} "Some body 2")))

          (testing "and when going back to the recycle bin"
            (eta/back driver)
            (eta/wait-visible driver {:css ".page__trash"})
            (eta/wait-visible driver {:css "ul.search-results"})

            (testing "displays the archived notes"
              (is (note-visible? driver "Some body 1"))
              (is (not (note-visible? driver "Some body 2")))
              (is (not (note-visible? driver "Some body 3"))))))))))

(deftest delete-test
  (usys/with-webdriver [driver base-url {fix "archived.edn"}]
    (testing "when visiting the recycle bin"
      (eta/go driver (str base-url "/bin"))
      (eta/wait-visible driver {:css ".page__trash"})

      (testing "and when permanently deleting a note"
        (let [note-id (-> fix second :notes/id)]
          (web/click! driver {:xpath (format "//li[@id='%s']
                                              //button[contains(@class,'note__delete-button')]"
                                             note-id)})
          (eta/wait-visible driver {:css ".modal-container.is-active"})
          (web/click! driver {:css ".modal-container .note__confirm-delete"})
          (eta/wait-absent driver {:css ".modal-container"})

          (testing "displays the archived notes"
            (is (note-visible? driver "Some body 1"))
            (is (not (note-visible? driver "Some body 2")))
            (is (not (note-visible? driver "Some body 3")))))))))
