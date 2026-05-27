(ns brainard.test.ui.notes.history-test
  (:require
    [brainard.infra.utils.edn :as edn]
    [brainard.test.ui-system :as ui-sys]
    [brainard.test.ui.utils :as ui-utils]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(defn ^:private collect-attachments [driver css-prefix]
  (into #{}
        (map (partial eta/get-element-text-el driver))
        (eta/query-all driver {:css (str css-prefix " ul.attachment-list li")})))

(defn ^:private collect-todos [driver css-prefix]
  (into {}
        (map (fn [todo]
               (let [txt (eta/get-element-text-el driver todo)
                     chbox (eta/query-from-shadow-root-el driver
                                                          todo
                                                          {:css "input[type=checkbox]:disabled"})
                     checked (eta/get-element-attr-el driver chbox "checked")]
                 [txt (some? checked)])))
        (eta/query-all driver {:css (str css-prefix " .todo-list .todo")})))

(defn ^:private collect-tags [driver css-prefix]
  (into #{}
        (map (comp edn/read-string (partial eta/get-element-text-el driver)))
        (eta/query-all driver {:css (str css-prefix " .tag-list .tag")})))

(deftest view-history-test
  (ui-sys/with-system [driver base-url {fix "history.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when viewing the note history"
          (ui-utils/click driver {:css "button.note__history-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__modal"})
          (let [[ver-1 ver-2 ver-3 ver-4 ver-5 ver-6]
                (for [li (eta/query-all driver {:css "ul.note-history > li"})]
                  (-> (eta/get-element-text-el driver li)
                      (string/replace #"\s+" " ")))]

            (testing "summarizes initial version"
              (let [todo-updates (second (string/split ver-1 #"Todos"))]
                (is (string/includes? ver-1 "Topic added Some context"))
                (is (string/includes? ver-1 "Pin added true"))
                (is (string/includes? ver-1 "Body added Some body"))
                (is (string/includes? ver-1 "Tags added :bar, :baz/quux, :foo"))
                (is (false? (string/includes? ver-1 "Attachments")))
                (is (string/includes? todo-updates "added Do a thing marked incomplete"))))

            (testing "summarizes version 2 changes"
              (let [attach-updates (second (string/split ver-2 #"Attachments"))]
                (is (string/includes? ver-2 "Body changed Some body to Some edited body goes here"))
                (is (string/includes? ver-2 "Tags removed :bar"))
                (is (string/includes? attach-updates "added some-pdf.pdf"))
                (is (string/includes? attach-updates "added sample.txt"))
                (is (false? (string/includes? ver-2 "Todos")))))

            (testing "summarizes version 3 changes"
              (let [attach-updates (second (string/split ver-3 #"Attachments"))
                    todo-updates (second (string/split ver-3 #"Todos"))]
                (is (string/includes? attach-updates "added image.jpg"))
                (is (false? (string/includes? attach-updates "some-pdf.pdf")))
                (is (false? (string/includes? attach-updates "sample.txt")))
                (is (string/includes? todo-updates "added Do another thing marked incomplete"))
                (is (false? (string/includes? todo-updates "Do a thing")))))

            (testing "summarizes version 4 changes"
              (let [todo-updates (second (string/split ver-4 #"Todos"))]
                (is (string/includes? ver-4 "Pin changed true to false"))
                (is (false? (string/includes? ver-4 "Attachments")))
                (is (string/includes? todo-updates "changed Do a thing to Did a thing marked complete"))))

            (testing "summarizes version 5 changes"
              (let [attach-updates (second (string/split ver-5 #"Attachments"))
                    todo-updates (second (string/split ver-5 #"Todos"))]
                (is (string/includes? attach-updates "changed image.jpg to some other name"))
                (is (false? (string/includes? todo-updates "Do a thing")))
                (is (false? (string/includes? todo-updates "Did a thing")))
                (is (string/includes? todo-updates "Do another thing marked complete"))
                (is (string/includes? todo-updates "added Do some third thing marked incomplete"))))

            (testing "summarizes version 6 changes"
              (let [attach-updates (second (string/split ver-6 #"Attachments"))
                    todo-updates (second (string/split ver-6 #"Todos"))]
                (is (string/includes? ver-6 "Topic changed Some context to Some new context"))
                (is (string/includes? ver-6 "Body changed Some edited body goes here to Some completely different body"))
                (is (string/includes? ver-6 "Tags added :other/tag removed :baz/quux"))
                (is (string/includes? attach-updates "removed some-pdf.pdf"))
                (is (false? (string/includes? attach-updates "image.jpg")))
                (is (false? (string/includes? attach-updates "sample.txt")))
                (is (false? (string/includes? attach-updates "some other name")))
                (is (string/includes? todo-updates "removed Do another thing"))
                (is (string/includes? todo-updates "changed Do some third thing to Do some third thing still"))
                (is (false? (string/includes? todo-updates "complete")))))

            (testing "and when showing version 1"
              (ui-utils/click driver {:xpath "(//button[contains(@class,'note__history-show')])[1]"})
              (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})
              (testing "displays the 1st version of the note"
                (is (eta/exists? driver {:css ".history__view .lni-paperclip"}))
                (is (eta/has-text? driver {:css ".history__view h1"} "Some context"))
                (is (eta/has-text? driver {:css ".history__view .content p"} "Some body")))

              (testing "displays the 1st version attachments"
                (is (false? (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='Attachments:']"})))
                (is (= #{} (collect-attachments driver ".history__view"))))

              (testing "displays the 1st version todos"
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='TODOs:']"}))
                (is (= {"Do a thing" false} (collect-todos driver ".history__view"))))

              (testing "displays the 1st version tags"
                (is (= #{:foo :bar :baz/quux} (collect-tags driver ".history__view"))))

              (ui-utils/click driver {:css ".history__view .panel-heading button.button"})
              (eta/wait-invisible driver {:css ".modal-container.is-active .modal-item.history__view"}))

            (testing "and when showing version 4"
              (ui-utils/click driver {:xpath "(//button[contains(@class,'note__history-show')])[4]"})
              (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})
              (testing "displays the 4th version of the note"
                (is (false? (eta/exists? driver {:css ".history__view .lni-paperclip"})))
                (is (eta/has-text? driver {:css ".history__view h1"} "Some context"))
                (is (eta/has-text? driver {:css ".history__view .content p"} "Some edited body goes here")))
              (testing "displays the 4th version attachments"
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='Attachments:']"}))
                (is (= #{"some-pdf.pdf" "sample.txt" "image.jpg"} (collect-attachments driver ".history__view"))))

              (testing "displays the 4th version todos"
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='TODOs:']"}))
                (is (= {"Did a thing"      true
                        "Do another thing" false}
                       (collect-todos driver ".history__view"))))

              (testing "displays the 4th version tags"
                (is (= #{:foo :baz/quux} (collect-tags driver ".history__view"))))

              (ui-utils/click driver {:css ".history__view .panel-heading button.button"})
              (eta/wait-invisible driver {:css ".modal-container.is-active .modal-item.history__view"}))

            (testing "and when showing version 6"
              (ui-utils/click driver {:xpath "(//button[contains(@class,'note__history-show')])[6]"})
              (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})
              (testing "displays the 6th version of the note"
                (is (false? (eta/exists? driver {:css ".history__view .lni-paperclip"})))
                (is (eta/has-text? driver {:css ".history__view h1"} "Some new context"))
                (is (eta/has-text? driver {:css ".history__view .content p"} "Some completely different body")))

              (testing "displays the 6th version attachments"
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='Attachments:']"}))
                (is (= #{"sample.txt" "some other name"} (collect-attachments driver ".history__view"))))

              (testing "displays the 6th version todos"
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='TODOs:']"}))
                (is (= {"Did a thing"               true
                        "Do some third thing still" false}
                       (collect-todos driver ".history__view"))))

              (testing "displays the 6th version tags"
                (is (= #{:foo :other/tag} (collect-tags driver ".history__view"))))

              (ui-utils/click driver {:css ".history__view .panel-heading button.button"})
              (eta/wait-invisible driver {:css ".modal-container.is-active .modal-item.history__view"}))))))))
