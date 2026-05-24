(ns brainard.test.ui.core-test
  (:require
    [brainard.test.ui-system :as ui-sys]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

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

(def ^:private ^:const node-item-selector-fmt
  "//li[contains(@class,'node-item')]//span[text()='%s']")

(deftest workspace-test
  (ui-sys/with-system [driver base-url]
    (letfn [(wait! [text]
              (eta/wait-visible driver {:xpath (format node-item-selector-fmt text)}))
            (edit! [node-text icon-class]
              (let [edit-selector-fmt (str node-item-selector-fmt
                                           "/following::i[contains(@class,'%s')][1]")
                    xpath (format edit-selector-fmt node-text icon-class)]
                (wait! node-text)
                (eta/click-el driver (eta/query driver {:xpath xpath}))))
            (submit! [content]
              (ui-sys/submit-form! driver
                                   ".modal-container.is-active form.form"
                                   {"Content" content}))
            (nodes [node-text]
              (eta/query-all driver {:xpath (format node-item-selector-fmt node-text)}))]
      (testing "when visiting the home page"
        (eta/go driver base-url)

        (testing "renders the empty workspace"
          (eta/wait-visible driver {:xpath "//h1/strong[text()='Workspace']"})
          (is (empty? (eta/query-all driver {:css "li.node-item"}))))

        (testing "and when creating a workspace root node"
          (eta/click driver {:css "div.drag-n-drop + button"})
          (submit! "root node")

          (testing "renders the updated workspace"
            (wait! "root node")
            (is (= 1 (count (eta/query-all driver {:css "li.node-item"}))))

            (testing "and when creating a child node"
              (edit! "root node" "lni-plus")
              (submit! "child node")

              (testing "renders the updated workspace"
                (wait! "child node")
                (is (= 2 (count (eta/query-all driver {:css "li.node-item"}))))

                (testing "and when creating a sibling node"
                  (edit! "root node" "lni-plus")
                  (submit! "sibling node")

                  (testing "renders the updated workspace"
                    (wait! "sibling node")
                    (is (= 3 (count (eta/query-all driver {:css "li.node-item"}))))))

                (testing "and when creating a grandchild node"
                  (edit! "child node" "lni-plus")
                  (submit! "grandchild node")

                  (testing "renders the updated workspace"
                    (wait! "grandchild node")
                    (is (= 4 (count (eta/query-all driver {:css "li.node-item"})))))))

              (testing "and when updating the child node"
                (edit! "child node" "lni-pencil")
                (submit! "updated child")

                (testing "renders the updated workspace"
                  (wait! "updated child")
                  (is (empty? (nodes "child node"))))))))))))
