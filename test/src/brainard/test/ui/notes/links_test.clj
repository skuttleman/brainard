(ns brainard.test.ui.notes.links-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.test :refer [deftest is testing]]
   [etaoin.api :as eta]))

(deftest bidirectional-linking-test
  (usys/with-webdriver [driver base-url {fix "links.edn"}]
    (let [[n1 n2 n3 n4] (map :notes/id fix)]
      (testing "when visiting the note with links"
        (eta/go driver (str base-url "/notes/" n3))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "links to note 1"
          (let [link (eta/query driver {:xpath "//ul[contains(@class,'note-links')]
                                                //a[contains(text(),'Note 1')]"})]
            (is (= (str "/notes/" n1) (eta/get-element-attr-el driver link "href")))))

        (testing "links to note 2"
          (let [link (eta/query driver {:xpath "//ul[contains(@class,'note-links')]
                                                //a[contains(text(),'Note 2')]"})]
            (is (= (str "/notes/" n2) (eta/get-element-attr-el driver link "href")))))

        (testing "and when visiting a note that is linked"
          (eta/go driver (str base-url "/notes/" n1))
          (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

          (testing "links to note 3"
            (let [link (eta/query driver {:xpath "//ul[contains(@class,'note-links')]
                                                  //a[contains(text(),'Note 3')]"})]
              (is (= (str "/notes/" n3) (eta/get-element-attr-el driver link "href"))))))

        (testing "and when visiting a note with no links"
          (eta/go driver (str base-url "/notes/" n4))
          (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

          (testing "Has no note links"
            (is (not (eta/exists? driver {:css "ul.note-links"})))))))))

(deftest note-linking-test
  (usys/with-webdriver [driver base-url {fix "links.edn"}]
    (let [[n1 _ n3 n4] (map :notes/id fix)]
      (testing "when visiting a linked note"
        (eta/go driver (str base-url "/notes/" n1))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when clicking the edit button"
          (web/click! driver {:css "button.note__edit-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

          (testing "and when searching for note to link"
            (web/click! driver {:css "button.note__create-link-button"})
            (eta/wait-visible driver {:css ".modal-container.is-active .note-edit__link"})
            (web/fill-field! driver "Search for a note" "Note")

            (testing "and when search results are displayed"
              (eta/wait-visible driver {:css ".note-edit__link .type-ahead ul.dropdown-content"})

              (testing "includes linkable notes"
                (let [options (into #{}
                                    (map (partial eta/get-element-inner-html-el driver))
                                    (eta/query-all driver {:css ".note-edit__link
                                                                 .type-ahead
                                                                 ul.dropdown-content li"}))]

                  (is (= #{"Note 2" "Note 4"} options))))

              (testing "and when selecting a note to link"
                (web/click! driver {:xpath "//*[contains(@class,'note-edit__link')]
                                            //li[contains(text(),'Note 4')]"})
                (eta/wait-absent driver {:css ".modal-container.is-active .note-edit__link"})

                (testing "and when saving the note"
                  (web/click! driver {:css ".modal-container.is-active button.submit"})
                  (eta/wait-absent driver {:css ".modal-container.is-active"})

                  (testing "links the note"
                    (let [links (into #{}
                                      (map (juxt #(eta/get-element-text-el driver %)
                                                 #(eta/get-element-attr-el driver % "href")))
                                      (eta/query-all driver {:css "ul.note-links > li > a"}))]
                      (is (= #{["Note 3" (str "/notes/" n3)]
                               ["Note 4" (str "/notes/" n4)]}
                             links)))))))))))))
