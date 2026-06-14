(ns brainard.test.ui.core.workspace-test
  (:require
   [brainard.test.harness.ui.system :as usys]
   [brainard.test.harness.ui.web :as web]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [etaoin.api :as eta]))

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
                (ws-edit! driver "child node" "lni-pencil-1")
                (ws-submit! driver "updated child")
                (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

                (testing "renders the updated workspace"
                  (is (not (eta/exists? driver {:xpath "//span[text()='Note 1B']"})))
                  (is (node-absent? "child node")))

                (testing "and when deleting the updated child"
                  (ws-edit! driver "updated child" "lni-trash-3")
                  (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
                  (web/click! driver {:css ".modal-container.is-active button.delete-node"})
                  (eta/wait-absent driver {:css ".modal-container.is-active .modal-item"})

                  (testing "renders the updated workspace"
                    (is (node-absent? "updated child"))
                    (is (node-absent? "grandchild node"))
                    (is (= 2 (count (eta/query-all driver {:css "li.node-item"}))))))))))))))

(deftest rearrangement-test
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
