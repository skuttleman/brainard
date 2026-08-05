(ns brainard.test.ui.core.hotkeys-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.test :refer [deftest is testing]]
   [etaoin.api :as eta]
   [etaoin.keys :as ek]))

(deftest nav-test
  (usys/with-webdriver [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when navigating rightward with hotkey"
        (eta/fill-active driver (ek/chord ek/alt-left ek/tab))
        (testing "visits the search page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))
          (is (eta/exists? driver {:css ".page__search"})))

        (eta/fill-active driver (ek/chord ek/alt-left ek/tab))
        (testing "visits the buzz page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__buzz"}))
          (is (eta/exists? driver {:css ".page__buzz"})))

        (eta/fill-active driver (ek/chord ek/alt-left ek/tab))
        (testing "visits the recycling page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__trash"}))
          (is (eta/exists? driver {:css ".page__trash"})))

        (eta/fill-active driver (ek/chord ek/alt-left ek/tab))
        (testing "visits the home page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))
          (is (eta/exists? driver {:css ".page__home"}))))

      (testing "and when navigating leftward with hotkey"
        (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left ek/tab))
        (testing "visits the recycling page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__trash"}))
          (is (eta/exists? driver {:css ".page__trash"})))

        (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left ek/tab))
        (testing "visits the buzz page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__buzz"}))
          (is (eta/exists? driver {:css ".page__buzz"})))

        (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left ek/tab))
        (testing "visits the search page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))
          (is (eta/exists? driver {:css ".page__search"})))

        (eta/fill-active driver (ek/chord ek/alt-left ek/shift-left ek/tab))
        (testing "visits the home page"
          (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))
          (is (eta/exists? driver {:css ".page__home"})))))))

(deftest fulltext-search-test
  (usys/with-webdriver [driver base-url {fix "search.edn"}]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

      (testing "and when activating the search modal"
        (eta/fill-active driver (ek/chord ek/control-left ek/space))
        (web/wait-optimistic #(eta/visible? driver {:css ".modal-container.is-active .fulltext__modal"}))

        (testing "and when searching for a note"
          (eta/fill driver
                    {:css ".fulltext__modal input.input"}
                    "one")
          (web/wait-optimistic #(eta/visible? driver {:css ".fulltext__modal .dropdown-content"}))

          (testing "displays the matching notes"
            (let [items (eta/query-all driver
                                       {:css ".fulltext__modal .dropdown-content .dropdown-item"})]
              (is (= #{"Note one A" "Note one B" "Note one C"}
                     (into #{}
                           (map (partial eta/get-element-inner-html-el driver))
                           items)))

              (testing "and when selecting a note"
                (web/click! driver {:xpath "//*[contains(@class,'dropdown-item') and contains(text(),'Note one B')]"})

                (testing "navigates to the note page"
                  (let [note-id (->> fix
                                     (filter (comp #{"Note one B"} :notes/body))
                                     first
                                     :notes/id)]
                    (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))
                    (is (= (str base-url "/notes/" note-id) (eta/get-url driver)))))))))))))

(deftest recent-note-test
  (usys/with-webdriver [driver base-url {fix "search.edn"}]
    (let [note-id (->> fix
                       (filter (comp #{"Note one B"} :notes/body))
                       first
                       :notes/id)]
      (testing "when visiting a note"
        (eta/go driver (str base-url "/notes/" note-id))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

        (testing "and when navigating away"
          (eta/fill-active driver (ek/chord ek/alt-left ek/tab))
          (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

          (testing "and when visiting most recent note"
            (eta/fill-active driver (ek/chord ek/meta-left ek/enter))
            (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))
            (testing "returns to the note page"
              (is (= (str base-url "/notes/" note-id) (eta/get-url driver))))

            (testing "and when creating a new note"
              (eta/go driver base-url)
              (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))
              (web/click! driver {:css "button.note__create-button"})
              (eta/wait-visible driver {:css ".modal-container.is-active form.form"})
              (web/submit-form! driver
                                ".modal-container.is-active form.form"
                                {"Topic" "Test Context"
                                 "Body"  "Brand new note"})
              (eta/wait-invisible driver {:css ".modal-container.is-active"})
              (eta/wait-visible driver {:css ".toast-message.is-success"})

              (testing "and when visiting most recent note"
                (eta/fill-active driver (ek/chord ek/meta-left ek/enter))
                (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

                (testing "goes to the note page"
                  (is (= "Brand new note" (eta/get-element-text driver {:css ".content"}))))

                (testing "and when opening a modal"
                  (eta/go driver base-url)
                  (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))
                  (web/click! driver {:css "button.note__create-button"})
                  (eta/wait-visible driver {:css ".modal-container.is-active form.form"})

                  (testing "and when visiting most recent note"
                    (eta/fill-active driver (ek/chord ek/meta-left ek/enter))

                    (testing "does not navigate"
                      (Thread/sleep 1000)
                      (is (eta/visible? driver {:css ".modal-container.is-active form.form"}))
                      (is (contains? #{base-url (str base-url "/")}
                                     (eta/get-url driver))))))))))))))
