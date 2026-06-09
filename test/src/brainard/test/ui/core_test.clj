(ns brainard.test.ui.core-test
  (:require
    [brainard.test.harness.ui.system :as usys]
    [brainard.test.harness.ui.web :as web]
    [cljc.java-time.day-of-week :as dow]
    [cljc.java-time.zoned-date-time :as zdt]
    [cljc.java-time.zone-offset :as zo]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]
    [whet.utils.navigation :as nav]))

(def ^:private ^:const node-item-selector-fmt
  "//li[contains(@class,'node-item')]//span[text()='%s']")

(defn ^:private ws-edit! [driver node-text icon-class]
  (let [edit-selector-fmt (str node-item-selector-fmt
                               "/following::i[contains(@class,'%s')][1]")
        xpath (format edit-selector-fmt node-text icon-class)]
    (eta/wait-visible driver {:xpath (format node-item-selector-fmt node-text)})
    (web/click! driver {:xpath xpath})))

(defn ^:private ws-submit! [driver text]
  (web/submit-form! driver
                    ".modal-container.is-active form.form"
                    {"Content" text}))

(defn ^:private note-visible? [driver body]
  (let [fmt "//ul[contains(@class,'search-results')]//span[contains(@class,'truncate') and text()='%s']"]
    (eta/exists? driver {:xpath (format fmt body)})))

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

(deftest workspace-test
  (usys/with-webdriver [driver base-url]
    (letfn [(node-absent? [node-text]
              (not (eta/exists? driver {:xpath (format node-item-selector-fmt node-text)})))]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "renders the empty workspace"
          (eta/wait-visible driver {:css "h1.workspace"})
          (is (empty? (eta/query-all driver {:css "li.node-item"}))))

        (testing "and when creating a workspace root node"
          (web/click! driver {:css ".drag-n-drop + .add-root-node"})
          (ws-submit! driver "root node")
          (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

          (testing "renders the updated workspace"
            (is (= 1 (count (eta/query-all driver {:css "li.node-item"}))))

            (testing "and when creating a child node"
              (ws-edit! driver "root node" "lni-plus")
              (ws-submit! driver "child node")
              (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

              (testing "renders the updated workspace"
                (is (= 2 (count (eta/query-all driver {:css "li.node-item"}))))

                (testing "and when creating a sibling node"
                  (ws-edit! driver "root node" "lni-plus")
                  (ws-submit! driver "sibling node")
                  (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

                  (testing "renders the updated workspace"
                    (is (= 3 (count (eta/query-all driver {:css "li.node-item"}))))))

                (testing "and when creating a grandchild node"
                  (ws-edit! driver "child node" "lni-plus")
                  (ws-submit! driver "grandchild node")
                  (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

                  (testing "renders the updated workspace"
                    (is (= 4 (count (eta/query-all driver {:css "li.node-item"})))))))

              (testing "and when updating the child node"
                (ws-edit! driver "child node" "lni-pencil")
                (ws-submit! driver "updated child")
                (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

                (testing "renders the updated workspace"
                  (is (not (eta/exists? driver {:xpath "//span[text()='Note 1B']"})))
                  (is (node-absent? "child node")))

                (testing "and when deleting the updated child"
                  (ws-edit! driver "updated child" "lni-trash-can")
                  (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
                  (web/click! driver {:css ".modal-container.is-active button.delete-node"})
                  (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

                  (testing "renders the updated workspace"
                    (is (node-absent? "updated child"))
                    (is (node-absent? "grandchild node"))
                    (is (= 2 (count (eta/query-all driver {:css "li.node-item"}))))))))))))))

(deftest workspace-rearrangement-test
  (usys/with-webdriver [driver base-url]
    (letfn [(drag-node! [source-text target-text]
              (let [event-ops "{bubbles: true, cancelable: true, view: window}"
                    script-fmt (str "const xpath = (q) => document.evaluate(
                                       q,
                                       document,
                                       null,
                                       XPathResult.FIRST_ORDERED_NODE_TYPE,
                                       null
                                     ).singleNodeValue
                                     const src = xpath(\"" node-item-selector-fmt "\");
                                     const tgt = xpath(\"" node-item-selector-fmt "\")
                                       .closest('li.node-item')
                                       .querySelector('div[data-target]');
                                     const opts = %s;
                                     src.dispatchEvent(new MouseEvent('mousedown', opts));
                                     tgt.dispatchEvent(new MouseEvent('mousemove', opts));
                                     window.dispatchEvent(new MouseEvent('mouseup', opts));")]
                (eta/js-execute driver (format script-fmt source-text target-text event-ops))))
            (reorder-node! [source-text after-text]
              (let [event-ops "{bubbles: true, cancelable: true, view: window}"
                    src-xpath (format node-item-selector-fmt source-text)
                    tgt-xpath (format node-item-selector-fmt after-text)
                    xpath-fn "const xpath = (q) => document.evaluate(q, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;"]
                (eta/js-execute driver
                                (str xpath-fn
                                     "xpath(\"" src-xpath "\").dispatchEvent(new MouseEvent('mousedown', "
                                     event-ops "));"))
                (eta/wait-exists driver {:css ".node-item *[data-target]"})
                (eta/js-execute driver
                                (str xpath-fn
                                     "const opts = " event-ops ";"
                                     "const li = xpath(\"" tgt-xpath "\").closest('li.node-item');"
                                     "li.nextElementSibling.querySelector('div[data-target]').dispatchEvent(new MouseEvent('mousemove', opts));"
                                     "const target = document.querySelector('.drag-n-drop > .node-list');"
                                     "target.dispatchEvent(new MouseEvent('mouseup', opts));"))))
            (node-order []
              (->> (eta/query-all driver {:css "li.node-item .node-content"})
                   (map (comp string/trim (partial eta/get-element-text-el driver)))))
            (root-nodes []
              (eta/query-all driver {:css ".root-node-list > li.node-item"}))]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (web/wait-optimistic #(eta/visible? driver {:css ".page__home"}))

        (testing "and when creating a root node"
          (web/click! driver {:css ".drag-n-drop + .add-root-node"})
          (ws-submit! driver "alpha")
          (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

          (testing "and when creating another root node with a child"
            (web/click! driver {:css ".drag-n-drop + .add-root-node"})
            (ws-submit! driver "beta")
            (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})
            (ws-edit! driver "beta" "lni-plus")
            (ws-submit! driver "gamma")
            (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

            (testing "renders the workspace with two root nodes"
              (is (= 2 (count (root-nodes))))
              (is (= 3 (count (eta/query-all driver {:css "li.node-item"})))))

            (testing "and when moving beta under alpha fails"
              (web/with-http-failure driver "drag-n-drop test failure"
                (drag-node! "beta" "alpha")

                (testing "closes the modal"
                  (eta/wait-absent driver {:css ".modal-container"})
                  (is (eta/absent? driver {:css ".modal-container"})))

                (testing "displays a toast message"
                  (eta/wait-visible driver {:css ".toast-message.is-danger"})
                  (is (eta/has-text? driver
                                     {:css ".toast-message.is-danger .body-text"}
                                     "drag-n-drop test failure"))
                  (eta/wait-absent driver {:css ".toast-message"}))))

            (testing "and when moving beta under alpha"
              (drag-node! "beta" "alpha")
              (web/wait-optimistic #(= 1 (count (root-nodes))))

              (testing "renders alpha as the only root with beta and gamma as descendants"
                (is (= 1 (count (root-nodes))))
                (is (= 3 (count (eta/query-all driver {:css "li.node-item"})))))

              (testing "and when adding a second child node to alpha"
                (ws-edit! driver "alpha" "lni-plus")
                (ws-submit! driver "delta")
                (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

                (testing "renders nodes in the correct order"
                  (is (= ["alpha" "beta" "gamma" "delta"] (node-order))))

                (testing "and when reordering beta after delta"
                  (reorder-node! "beta" "delta")
                  (web/wait-optimistic #(= ["alpha" "delta" "beta" "gamma"] (node-order)))

                  (testing "renders nodes in the new order"
                    (is (= ["alpha" "delta" "beta" "gamma"] (node-order)))))))))))))

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

(deftest search-test
  (usys/with-webdriver [driver base-url {fix "search.edn"}]
    (letfn [(go-to-search! []
              (eta/go driver (str base-url "/search"))
              (web/wait-optimistic #(eta/visible? driver {:css ".page__search"})))
            (open-dropdown! [label]
              (web/click! driver {:css (format ".form-field[data-field-label='%s'] button" label)})
              (eta/wait-visible driver {:css "ul.dropdown-items"}))
            (pick-option! [item-text]
              (let [item-fmt "//ul[contains(@class,'dropdown-items')]//span[text()='%s']"
                    active-fmt "//ul[contains(@class,'dropdown-items')]//li[contains(@class,'is-active')]//span[text()='%s']"]
                (web/click! driver {:xpath (format item-fmt item-text)})
                (web/wait-optimistic #(or (not (eta/exists? driver {:css "ul.dropdown-items"}))
                                          (eta/exists? driver {:xpath (format active-fmt item-text)})))))
            (search! []
              (web/click! driver {:css "form.search-form button.submit"})
              (eta/wait-visible driver {:css "ul.search-results"}))
            (url-query? [qp]
              (let [url (eta/get-url driver)
                    params (-> url
                               (string/split #"\?")
                               second
                               nav/->query-params)]
                (= qp params)))]
      (testing "when visiting the search page"
        (testing "and when filtering on a tag"
          (go-to-search!)
          (open-dropdown! "Tag Filter")
          (pick-option! ":tag/alpha")
          (search!)

          (testing "renders the correct notes"
            (is (note-visible? driver "Note A1"))
            (is (note-visible? driver "Note A2"))
            (is (note-visible? driver "Note B1"))
            (is (not (note-visible? driver "Note B2")))
            (is (not (note-visible? driver "Note C1"))))

          (testing "updates the browser url"
            (is (url-query? {:tags "tag/alpha"})))

          (testing "and when clicking the edit link"
            (web/click! driver {:css "ul.search-results > li .note__edit-link"})
            (eta/wait-visible driver {:css ".container.page__note"})
            (testing "renders the note page"
              (let [note-id (-> fix first :notes/id)]
                (is (= (str base-url "/notes/" note-id) (eta/get-url driver)))
                (is (eta/visible? driver {:xpath "//*[contains(@class,'content')]//*[text()='Note A1']"}))
                (eta/back driver))))

          (testing "and when navigating back"
            (eta/back driver)
            (eta/wait-absent driver {:css "ul.search-results"})
            (testing "clears the search results"
              (is (= (str base-url "/search") (eta/get-url driver)))
              (is (eta/absent? driver {:css "ul.search-results"}))))

          (testing "and when filtering on context"
            (open-dropdown! "Topic Filter")
            (pick-option! "Context A")
            (search!)

            (testing "renders the correct notes"
              (is (note-visible? driver "Note A1"))
              (is (note-visible? driver "Note A2"))
              (is (not (note-visible? driver "Note B1"))))))

        (testing "and when filtering on multiple tags"
          (go-to-search!)
          (open-dropdown! "Tag Filter")
          (pick-option! ":tag/alpha")
          (pick-option! ":tag/beta")
          (search!)

          (testing "renders the correct notes"
            (is (note-visible? driver "Note A2"))
            (is (not (note-visible? driver "Note A1")))
            (is (not (note-visible? driver "Note B1")))
            (is (not (note-visible? driver "Note B2"))))

          (testing "updates the browser url"
            (is (url-query? {:tags #{"tag/alpha" "tag/beta"}})))

          (testing "and when filtering on context"
            (open-dropdown! "Topic Filter")
            (pick-option! "Context A")
            (search!)

            (testing "renders the correct notes"
              (is (note-visible? driver "Note A2"))
              (is (not (note-visible? driver "Note B2"))))

            (testing "updates the browser url"
              (is (url-query? {:tags #{"tag/alpha" "tag/beta"} :context "Context A"}))))

          (testing "and when the filters yield no results"
            (go-to-search!)
            (open-dropdown! "Topic Filter")
            (pick-option! "Context B")
            (open-dropdown! "Tag Filter")
            (pick-option! ":tag/alpha")
            (pick-option! ":tag/beta")
            (web/click! driver {:css "form.search-form button.submit"})
            (eta/wait-absent driver {:css "ul.search-results"})
            (eta/wait-visible driver {:css "span.search-results"})

            (testing "does not render any notes"
              (is (eta/has-text? driver {:css ".search-results .message-body"} "No search results"))
              (is (not (note-visible? driver "Note A1")))
              (is (not (note-visible? driver "Note A2")))
              (is (not (note-visible? driver "Note B1")))
              (is (not (note-visible? driver "Note B2")))
              (is (not (note-visible? driver "Note C1"))))))

        (testing "and when filtering on context"
          (go-to-search!)
          (open-dropdown! "Topic Filter")
          (pick-option! "Context B")
          (search!)

          (testing "renders the correct notes"
            (is (note-visible? driver "Note B1"))
            (is (note-visible? driver "Note B2"))
            (is (not (note-visible? driver "Note A1")))
            (is (not (note-visible? driver "Note C1"))))

          (testing "updates the browser url"
            (is (url-query? {:context "Context B"}))))))))

(deftest buzz-test
  (usys/with-webdriver [driver base-url {buzz "buzz.edn"}]
    (let [note-id-2 (->> buzz
                         (filter (comp #{"Note 2"} :notes/body))
                         first
                         :notes/id)
          day-of-the-week (-> (zdt/now zo/utc)
                              zdt/get-day-of-week
                              dow/name
                              string/lower-case
                              keyword)]
      (testing "when visiting the buzz page"
        (eta/go driver (str base-url "/buzz"))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__buzz"}))

        (testing "renders the correct notes"
          (is (note-visible? driver "Note 1"))
          (is (not (note-visible? driver "Note 2"))))

        (testing "and when editing a note"
          (eta/go driver (str base-url "/notes/" note-id-2))
          (eta/wait-visible driver {:css "form.schedule-form"})

          (testing "and when adding a schedule to the note"
            (web/submit-form! driver "form.schedule-form" {"Day of the week" day-of-the-week})
            (eta/wait-absent driver {:css "p.schedules__empty"})

            (testing "and when visiting the buzz page"
              (eta/go driver (str base-url "/buzz"))
              (eta/wait-visible driver {:css "ul.search-results"})

              (testing "renders the correct notes"
                (is (note-visible? driver "Note 1"))
                (is (note-visible? driver "Note 2"))))))))))
