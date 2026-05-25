(ns brainard.test.ui.core-test
  (:require
    [brainard.test.ui-system :as ui-sys]
    [brainard.test.ui.utils :as ui-utils]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]
    [whet.utils.navigation :as nav])
  (:import (java.time LocalDate)))

(def ^:private ^:const node-item-selector-fmt
  "//li[contains(@class,'node-item')]//span[text()='%s']")

(defn ^:private wait! [driver text]
  (eta/wait-visible driver {:xpath (format node-item-selector-fmt text)}))

(defn ^:private ws-edit! [driver node-text icon-class]
  (let [edit-selector-fmt (str node-item-selector-fmt
                               "/following::i[contains(@class,'%s')][1]")
        xpath (format edit-selector-fmt node-text icon-class)]
    (wait! driver node-text)
    (ui-utils/click driver {:xpath xpath})))

(defn ^:private ws-submit! [driver text]
  (ui-utils/submit-form! driver
                         ".modal-container.is-active form.form"
                         {"Content" text}))

(defn ^:private note-visible? [driver body]
  (let [fmt "//ul[contains(@class,'search-results')]//span[contains(@class,'truncate') and text()='%s']"]
    (eta/exists? driver {:xpath (format fmt body)})))

(deftest navigation-test
  (ui-sys/with-system [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
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
  (ui-sys/with-system [driver base-url]
    (letfn [(node-absent? [node-text]
              (not (eta/exists? driver {:xpath (format node-item-selector-fmt node-text)})))]
      (testing "when visiting the home page"
        (eta/go driver base-url)

        (testing "renders the empty workspace"
          (eta/wait-visible driver {:css "h1.workspace"})
          (is (empty? (eta/query-all driver {:css "li.node-item"}))))

        (testing "and when creating a workspace root node"
          (ui-utils/click driver {:css ".drag-n-drop + .add-root-node"})
          (ws-submit! driver "root node")

          (testing "renders the updated workspace"
            (wait! driver "root node")
            (is (= 1 (count (eta/query-all driver {:css "li.node-item"}))))

            (testing "and when creating a child node"
              (ws-edit! driver "root node" "lni-plus")
              (ws-submit! driver "child node")

              (testing "renders the updated workspace"
                (wait! driver "child node")
                (is (= 2 (count (eta/query-all driver {:css "li.node-item"}))))

                (testing "and when creating a sibling node"
                  (ws-edit! driver "root node" "lni-plus")
                  (ws-submit! driver "sibling node")

                  (testing "renders the updated workspace"
                    (wait! driver "sibling node")
                    (is (= 3 (count (eta/query-all driver {:css "li.node-item"}))))))

                (testing "and when creating a grandchild node"
                  (ws-edit! driver "child node" "lni-plus")
                  (ws-submit! driver "grandchild node")

                  (testing "renders the updated workspace"
                    (wait! driver "grandchild node")
                    (is (= 4 (count (eta/query-all driver {:css "li.node-item"})))))))

              (testing "and when updating the child node"
                (ws-edit! driver "child node" "lni-pencil")
                (ws-submit! driver "updated child")

                (testing "renders the updated workspace"
                  (wait! driver "updated child")
                  (is (not (eta/exists? driver {:xpath "//span[text()='Note 1B']"})))
                  (is (node-absent? "child node")))

                (testing "and when deleting the updated child"
                  (ws-edit! driver "updated child" "lni-trash-can")
                  (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
                  (ui-utils/click driver {:css ".modal-container.is-active button.delete-node"})

                  (testing "renders the updated workspace"
                    (eta/wait-predicate #(node-absent? "updated child"))
                    (is (node-absent? "updated child"))
                    (is (node-absent? "grandchild node"))
                    (is (= 2 (count (eta/query-all driver {:css "li.node-item"}))))))))))))))

(deftest workspace-rearrangement-test
  (ui-sys/with-system [driver base-url]
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
                                     src.dispatchEvent(new MouseEvent('mousedown', %s));
                                     tgt.dispatchEvent(new MouseEvent('mousemove', %s));
                                     window.dispatchEvent(new MouseEvent('mouseup', %s));")]
                (eta/js-execute driver
                                (format script-fmt
                                        source-text
                                        target-text
                                        event-ops
                                        event-ops
                                        event-ops))))
            (reorder-node! [source-text after-text]
              (let [event-ops "{bubbles: true, cancelable: true, view: window}"
                    src-xpath (format node-item-selector-fmt source-text)
                    tgt-xpath (format node-item-selector-fmt after-text)
                    xpath-fn "const xpath = (q) => document.evaluate(q, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;"]
                (eta/js-execute driver
                                (str xpath-fn
                                     "xpath(\"" src-xpath "\").dispatchEvent(new MouseEvent('mousedown', "
                                     event-ops "));"))
                (eta/wait-exists driver {:css "li:not(.node-item) > div[data-target]"})
                (eta/js-execute driver
                                (str xpath-fn
                                     "const opts = " event-ops ";"
                                     "const li = xpath(\"" tgt-xpath "\").closest('li.node-item');"
                                     "li.nextElementSibling.querySelector('div[data-target]').dispatchEvent(new MouseEvent('mousemove', opts));"
                                     "window.dispatchEvent(new MouseEvent('mouseup', opts));"))))
            (node-order []
              (->> (eta/query-all driver {:css "li.node-item span.layout--space-after > span[style]"})
                   (map (comp string/trim (partial eta/get-element-text-el driver)))))
            (root-nodes []
              (eta/query-all driver {:css "div.drag-n-drop > ul.node-list > li.node-item"}))]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (testing "and when creating a root node"
          (ui-utils/click driver {:css "div.drag-n-drop + button"})
          (ws-submit! driver "alpha")
          (wait! driver "alpha")

          (testing "and when creating another root node with a child"
            (ui-utils/click driver {:css "div.drag-n-drop + button"})
            (ws-submit! driver "beta")
            (wait! driver "beta")
            (ws-edit! driver "beta" "lni-plus")
            (ws-submit! driver "gamma")
            (wait! driver "gamma")

            (testing "renders the workspace with two root nodes"
              (is (= 2 (count (root-nodes))))
              (is (= 3 (count (eta/query-all driver {:css "li.node-item"})))))

            (testing "and when moving beta under alpha"
              (drag-node! "beta" "alpha")
              (eta/wait-predicate #(= 1 (count (root-nodes))))

              (testing "renders alpha as the only root with beta and gamma as descendants"
                (is (= 1 (count (root-nodes))))
                (is (= 3 (count (eta/query-all driver {:css "li.node-item"})))))

              (testing "and when adding a second child node to alpha"
                (ws-edit! driver "alpha" "lni-plus")
                (ws-submit! driver "delta")
                (wait! driver "delta")

                (testing "renders nodes in the correct order"
                  (is (= ["alpha" "beta" "gamma" "delta"] (node-order))))

                (testing "and when reordering beta after delta"
                  (reorder-node! "beta" "delta")
                  (eta/wait-predicate #(= ["alpha" "delta" "beta" "gamma"] (node-order)))

                  (testing "renders nodes in the new order"
                    (is (= ["alpha" "delta" "beta" "gamma"] (node-order)))))))))))))

(deftest pinned-test
  (ui-sys/with-system [driver base-url {fix "pinned.edn"}]
    (letfn [(expand! [context]
              (let [xpath (format "//strong[text()='%s']/following::button[1]" context)]
                (ui-utils/click driver {:xpath xpath})))
            (note-body-visible? [body]
              (let [xpath (format "//span[contains(@class,'truncate') and text()='%s']" body)]
                (eta/visible? driver {:xpath xpath})))
            (edit-link [note-id]
              {:xpath (format "//li[@id='%s']//a[text()='edit']" note-id)})
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
          (eta/wait-visible driver {:xpath "//h1/strong[text()='Pinned notes']"})

          (testing "and when expanding Context 1"
            (expand! "Context 1")

            (testing "renders the correct Context 1 notes"
              (eta/wait-visible driver (edit-link note-id-1))
              (is (note-body-visible? "Note 1A"))
              (is (note-absent? "Note 1B"))
              (is (note-body-visible? "Note 1C")))

            (testing "does not render Context 2 notes"
              (is (note-absent? "Note 2A"))
              (is (note-absent? "Note 2B")))

            (testing "does not render Context 3 notes"
              (is (note-absent? "Note 3A")))

            (testing "can navigate to the note's edit page"
              (ui-utils/click driver (edit-link note-id-1))
              (eta/wait-predicate #(re-find #"/notes/" (eta/get-url driver)))
              (is (= (str base-url "/notes/" note-id-1)
                     (eta/get-url driver)))
              (eta/go driver base-url)
              (eta/wait-visible driver {:xpath "//h1/strong[text()='Pinned notes']"})))

          (testing "and when expanding Context 2"
            (expand! "Context 2")

            (testing "renders the correct Context 2 notes"
              (eta/wait-visible driver (edit-link note-id-2))
              (is (note-body-visible? "Note 2A"))
              (is (note-absent? "Note 2B")))

            (testing "does not render Context 1 notes"
              (is (note-absent? "Note 1A"))
              (is (note-absent? "Note 1B"))
              (is (note-absent? "Note 1C")))

            (testing "does not render Context 3 notes"
              (is (note-absent? "Note 3A")))

            (testing "can navigate to the note's edit page"
              (ui-utils/click driver (edit-link note-id-2))
              (eta/wait-predicate #(re-find #"/notes/" (eta/get-url driver)))
              (is (= (str base-url "/notes/" note-id-2)
                     (eta/get-url driver)))
              (eta/go driver base-url)
              (eta/wait-visible driver {:xpath "//h1/strong[text()='Pinned notes']"})))

          (testing "there is no section for Context 3"
            (is (not (eta/exists? driver {:xpath "//strong[text()='Context 3']"})))))))))

(deftest search-test
  (ui-sys/with-system [driver base-url {_ "search.edn"}]
    (letfn [(go-to-search! []
              (eta/go driver (str base-url "/search"))
              (eta/wait-visible driver {:css "form.form"}))
            (open-dropdown! [label]
              (ui-utils/click driver {:xpath (format "//label[text()='%s']/..//button" label)})
              (eta/wait-visible driver {:css "ul.dropdown-items"}))
            (pick-option! [item-text]
              (let [fmt "//ul[contains(@class,'dropdown-items')]//span[text()='%s']"]
                (ui-utils/click driver {:xpath (format fmt item-text)})))
            (search! []
              (ui-utils/click driver {:css "form.form button.submit"})
              (Thread/sleep 10)
              (eta/wait-visible driver {:css "ul.search-results"}))
            (url-query? [qp]
              (let [url (eta/get-url driver)
                    params (-> url
                               (string/split #"\?")
                               second
                               nav/->query-params)]
                (println "QUERY PARAMS:" params)
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

          (testing "and when filtering on context"
            (open-dropdown! "Topic Filter")
            (pick-option! "Context A")
            (search!)

            (testing "renders the correct notes"
              (is (note-visible? driver "Note A1"))
              (is (note-visible? driver "Note A2"))
              (is (not (note-visible? driver "Note B1"))))

            (testing "updates the browser url"
              (is (url-query? {:tags "tag/alpha" :context "Context A"})))))

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
              (is (url-query? {:tags #{"tag/alpha" "tag/beta"} :context "Context A"})))))

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
  (ui-sys/with-system [driver base-url {buzz "buzz.edn"}]
    (let [note-id-2 (->> buzz
                         (filter (comp #{"Note 2"} :notes/body))
                         first
                         :notes/id)
          day-of-the-week (-> (LocalDate/now)
                              .getDayOfWeek
                              .name
                              string/lower-case
                              keyword)]
      (testing "when visiting the buzz page"
        (eta/go driver (str base-url "/buzz"))
        (eta/wait-visible driver {:css "ul.search-results"})

        (testing "renders the correct notes"
          (is (note-visible? driver "Note 1"))
          (is (not (note-visible? driver "Note 2"))))

        (testing "and when editing a note"
          (eta/go driver (str base-url "/notes/" note-id-2))
          (eta/wait-visible driver {:css "form.form"})

          (testing "and when adding a schedule to the note"
            (ui-utils/submit-form! driver "form.form" {"Day of the week" day-of-the-week})
            (eta/wait-invisible driver {:xpath "//p/em[text()='no related schedules']"})

            (testing "and when visiting the buzz page"
              (eta/go driver (str base-url "/buzz"))
              (eta/wait-visible driver {:css "ul.search-results"})

              (testing "renders the correct notes"
                (is (note-visible? driver "Note 1"))
                (is (note-visible? driver "Note 2"))))))))))
