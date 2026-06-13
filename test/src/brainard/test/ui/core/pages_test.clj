(ns brainard.test.ui.core.pages-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.test :refer [deftest is testing]]
   [etaoin.api :as eta]))

(deftest navigation-test
  (usys/with-webdriver [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "renders app title"
        (eta/wait-visible driver {:css "h1.title"})
        (is (= "brainard" (eta/get-element-text driver {:css "h1.title"}))))

      (testing "renders pinned notes section"
        (is (true? (eta/wait-visible driver {:css "h1.pinned-notes"}))))

      (testing "renders workspace section"
        (is (true? (eta/wait-visible driver {:css "h1.workspace"}))))

      (testing "home nav item is active"
        (let [el (eta/query driver {:css ".navbar .is-active > .navbar-item"})]
          (is (= "Home" (eta/get-element-inner-html-el driver el))))))

    (testing "when visiting the search page"
      (eta/go driver (str base-url "/search"))
      (testing "renders search form"
        (is (true? (eta/wait-visible driver {:css "form.search-form"}))))

      (testing "search nav item is active"
        (let [el (eta/query driver {:css ".navbar .is-active > .navbar-item"})]
          (is (= "Search" (eta/get-element-inner-html-el driver el))))))

    (testing "when visiting the buzz page"
      (eta/go driver (str base-url "/buzz"))
      (testing "renders app title"
        (is (true? (eta/wait-visible driver {:css "h1.title"}))))

      (testing "buzz nav item is active"
        (let [el (eta/query driver {:css ".navbar .is-active > .navbar-item"})]
          (is (= "Buzz" (eta/get-element-inner-html-el driver el))))))))

(deftest pinned-test
  (usys/with-webdriver [driver base-url {fix "pinned.edn"}]
    (letfn [(expand! [context]
              (let [css (format ".context-group[data-context='%s'] .expand-context" context)]
                (web/click! driver {:css css})))
            (edit-link [note-id]
              {:xpath (format "//li[@id='%s']//a[text()='edit']" note-id)})
            (note-visible? [body]
              (let [xpath (format "//span[contains(@class,'truncate') and text()='%s']" body)]
                (eta/visible? driver {:xpath xpath})))
            (note-absent? [body]
              (let [xpath (format "//span[contains(@class,'truncate') and text()='%s']" body)]
                (not (eta/exists? driver {:xpath xpath}))))]
      (let [note-id-1 (->> fix
                           (filter (comp #{"Context 1"} :notes/context))
                           first
                           :notes/id)
            note-id-2 (->> fix
                           (filter (comp #{"Context 2"} :notes/context))
                           first
                           :notes/id)]
        (testing "when visiting the home page"
          (eta/go driver base-url)
          (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

          (testing "and when expanding Context 1"
            (expand! "Context 1")

            (testing "renders the correct Context 1 notes"
              (eta/wait-visible driver (edit-link note-id-1))
              (is (note-visible? "Note 1A"))
              (is (note-absent? "Note 1B"))
              (is (note-visible? "Note 1C")))

            (testing "does not render Context 2 notes"
              (is (note-absent? "Note 2A"))
              (is (note-absent? "Note 2B")))

            (testing "does not render Context 3 notes"
              (is (note-absent? "Note 3A")))

            (testing "can navigate to the note's edit page"
              (web/click! driver (edit-link note-id-1))
              (web/wait-optimistic #(re-find #"/notes/" (eta/get-url driver)))
              (is (= (str base-url "/notes/" note-id-1)
                     (eta/get-url driver)))
              (eta/go driver base-url)
              (eta/wait-visible driver {:css "h1.pinned-notes"})))

          (testing "and when expanding Context 2"
            (expand! "Context 2")

            (testing "renders the correct Context 2 notes"
              (eta/wait-visible driver (edit-link note-id-2))
              (is (note-visible? "Note 2A"))
              (is (note-absent? "Note 2B")))

            (testing "does not render Context 1 notes"
              (is (note-absent? "Note 1A"))
              (is (note-absent? "Note 1B"))
              (is (note-absent? "Note 1C")))

            (testing "does not render Context 3 notes"
              (is (note-absent? "Note 3A")))

            (testing "can navigate to the note's edit page"
              (web/click! driver (edit-link note-id-2))
              (web/wait-optimistic #(re-find #"/notes/" (eta/get-url driver)))
              (is (= (str base-url "/notes/" note-id-2)
                     (eta/get-url driver)))
              (eta/go driver base-url)
              (eta/wait-visible driver {:css "h1.pinned-notes"})))

          (testing "there is no section for Context 3"
            (is (not (eta/exists? driver {:css ".context-group[data-context='Context 3']"})))))))))
