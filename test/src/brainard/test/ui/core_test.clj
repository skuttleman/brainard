(ns brainard.test.ui.core-test
  (:require
    [brainard.test.ui-system :as ui-sys]
    [brainard.test.ui.utils :as ui-utils]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(def ^:private ^:const node-item-selector-fmt
  "//li[contains(@class,'node-item')]//span[text()='%s']")

(defn ^:private wait! [driver text]
  (eta/wait-visible driver {:xpath (format node-item-selector-fmt text)}))

(defn ^:private edit! [driver node-text icon-class]
  (let [edit-selector-fmt (str node-item-selector-fmt
                               "/following::i[contains(@class,'%s')][1]")
        xpath (format edit-selector-fmt node-text icon-class)]
    (wait! driver node-text)
    (eta/click-el driver (eta/query driver {:xpath xpath}))))

(defn ^:private submit! [driver text]
  (ui-utils/submit-form! driver
                         ".modal-container.is-active form.form"
                         {"Content" text}))

(deftest navigation-test
  (ui-sys/with-system [driver base-url]
    (testing "when visiting the home page"
      (eta/go driver base-url)
      (testing "renders app title"
        (eta/wait-visible driver {:css "h1.title"})
        (is (= "brainard" (eta/get-element-text driver {:css "h1.title"}))))

      (testing "renders pinned notes section"
        (is (true? (eta/wait-visible driver {:xpath "//h1/strong[text()='Pinned notes']"}))))

      (testing "renders workspace section"
        (is (true? (eta/wait-visible driver {:xpath "//h1/strong[text()='Workspace']"}))))

      (testing "home nav item is active"
        (let [el (eta/query driver {:css "li.is-active > a"})]
          (is (= "Home" (eta/get-element-inner-html-el driver el))))))

    (testing "when visiting the search page"
      (eta/go driver (str base-url "/search"))
      (testing "renders search form"
        (is (true? (eta/wait-visible driver {:xpath "//button[text()='Search']"}))))

      (testing "search nav item is active"
        (let [el (eta/query driver {:css "li.is-active > a"})]
          (is (= "Search" (eta/get-element-inner-html-el driver el))))))

    (testing "when visiting the buzz page"
      (eta/go driver (str base-url "/buzz"))
      (testing "renders app title"
        (is (true? (eta/wait-visible driver {:css "h1.title"}))))

      (testing "buzz nav item is active"
        (let [el (eta/query driver {:css "li.is-active > a"})]
          (is (= "Buzz" (eta/get-element-inner-html-el driver el))))))))

(deftest workspace-test
  (ui-sys/with-system [driver base-url]
    (letfn [(nodes [node-text]
              (eta/query-all driver {:xpath (format node-item-selector-fmt node-text)}))]
      (testing "when visiting the home page"
        (eta/go driver base-url)

        (testing "renders the empty workspace"
          (eta/wait-visible driver {:xpath "//h1/strong[text()='Workspace']"})
          (is (empty? (eta/query-all driver {:css "li.node-item"}))))

        (testing "and when creating a workspace root node"
          (eta/click driver {:css "div.drag-n-drop + button"})
          (submit! driver "root node")

          (testing "renders the updated workspace"
            (wait! driver "root node")
            (is (= 1 (count (eta/query-all driver {:css "li.node-item"}))))

            (testing "and when creating a child node"
              (edit! driver "root node" "lni-plus")
              (submit! driver "child node")

              (testing "renders the updated workspace"
                (wait! driver "child node")
                (is (= 2 (count (eta/query-all driver {:css "li.node-item"}))))

                (testing "and when creating a sibling node"
                  (edit! driver "root node" "lni-plus")
                  (submit! driver "sibling node")

                  (testing "renders the updated workspace"
                    (wait! driver "sibling node")
                    (is (= 3 (count (eta/query-all driver {:css "li.node-item"}))))))

                (testing "and when creating a grandchild node"
                  (edit! driver "child node" "lni-plus")
                  (submit! driver "grandchild node")

                  (testing "renders the updated workspace"
                    (wait! driver "grandchild node")
                    (is (= 4 (count (eta/query-all driver {:css "li.node-item"})))))))

              (testing "and when updating the child node"
                (edit! driver "child node" "lni-pencil")
                (submit! driver "updated child")

                (testing "renders the updated workspace"
                  (wait! driver "updated child")
                  (is (empty? (nodes "child node"))))))))))))

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
            (root-nodes []
              (eta/query-all driver {:css "div.drag-n-drop > ul.node-list > li.node-item"}))]
      (testing "when visiting the home page"
        (eta/go driver base-url)
        (testing "and when creating a root node"
          (eta/click driver {:css "div.drag-n-drop + button"})
          (submit! driver "alpha")
          (wait! driver "alpha")

          (testing "and when creating another root node with a child"
            (eta/click driver {:css "div.drag-n-drop + button"})
            (submit! driver "beta")
            (wait! driver "beta")
            (edit! driver "beta" "lni-plus")
            (submit! driver "gamma")
            (wait! driver "gamma")

            (testing "renders the workspace with two root nodes"
              (is (= 2 (count (root-nodes))))
              (is (= 3 (count (eta/query-all driver {:css "li.node-item"})))))

            (testing "and when moving beta under alpha"
              (drag-node! "beta" "alpha")
              (eta/wait-predicate #(= 1 (count (root-nodes))))

              (testing "renders alpha as the only root with beta and gamma as descendants"
                (is (= 1 (count (root-nodes))))
                (is (= 3 (count (eta/query-all driver {:css "li.node-item"}))))))))))))
