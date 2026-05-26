(ns brainard.test.ui.notes.history-test
  (:require
    [brainard.infra.utils.edn :as edn]
    [brainard.test.ui-system :as ui-sys]
    [brainard.test.ui.utils :as ui-utils]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest view-history-test
  (ui-sys/with-system [driver base-url {fix "history.edn"}]
    (let [note-id (-> fix first :notes/id)]
      (testing "when visiting the note page"
        (eta/go driver (str base-url "/notes/" note-id))
        (eta/wait-visible driver {:css "h1.layout--space-after"})

        (testing "and when viewing the note history"
          (ui-utils/click driver {:css "button.note__history-button"})
          (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__modal"})
          (eta/screenshot driver "foo.png")
          (let [[ver-1 ver-2 ver-3 ver-4 ver-5 ver-6]
                (for [li (eta/query-all driver {:css "ul.note-history > li"})]
                  (-> (eta/get-element-text-el driver li)
                      (string/replace #"\s+" " ")))]

            (testing "displays version 1 changes"
              (is (string/includes? ver-1 "Topic added Some context"))
              (is (string/includes? ver-1 "Pin added true"))
              (is (string/includes? ver-1 "Body added Some body"))
              (is (string/includes? ver-1 "Tags added :bar, :baz/quux, :foo")))

            (testing "displays version 2 changes"
              (is (string/includes? ver-2 "Body changed Some body to Some edited body goes here"))
              (is (string/includes? ver-2 "Tags removed :bar")))

            (testing "displays version 3 changes"
              (let [attach-updates (second (string/split ver-3 #"Attachments"))]
                (is (string/includes? attach-updates "added image.jpg"))))

            (testing "displays version 4 changes"
              (is (string/includes? ver-4 "Pin changed true to false")))

            (testing "displays version 5 changes"
              (let [attach-updates (second (string/split ver-5 #"Attachments"))]
                (is (string/includes? attach-updates "changed image.jpg to some other name"))))

            (testing "displays version 6 changes"
              (let [attach-updates (second (string/split ver-6 #"Attachments"))]
                (is (string/includes? ver-6 "Topic changed Some context to Some new context"))
                (is (string/includes? ver-6 "Body changed Some edited body goes here to Some completely different body"))
                (is (string/includes? ver-6 "Tags added :other/tag removed :baz/quux"))
                (is (string/includes? attach-updates "removed some-pdf.pdf"))
                (is (not (string/includes? attach-updates "changed image.jpg to some other name")))))

            (testing "and when showing version 1"
              (ui-utils/click driver {:xpath "(//button[contains(@class,'note__history-show')])[1]"})
              (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})
              (testing "displays the 1st version of the note"
                (is (eta/exists? driver {:css ".history__view .lni-paperclip"}))
                (is (eta/has-text? driver {:css ".history__view h1"} "Some context"))
                (is (eta/has-text? driver {:css ".history__view .content p"} "Some body"))
                (is (not (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='Attachments:']"})))
                (is (not (eta/exists? driver {:css ".history__view ul.attachment-list"})))
                (is (= #{:foo :bar :baz/quux}
                       (into #{}
                             (map (comp edn/read-string (partial eta/get-element-text-el driver)))
                             (eta/query-all driver {:css ".history__view .tag-list .tag"}))))
                (ui-utils/click driver {:css ".history__view .panel-heading button.button"})
                (eta/wait-invisible driver {:css ".modal-container.is-active .modal-item.history__view"})))

            (testing "and when showing version 2"
              (ui-utils/click driver {:xpath "(//button[contains(@class,'note__history-show')])[2]"})
              (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})
              (testing "displays the 2nd version of the note"
                (is (eta/exists? driver {:css ".history__view .lni-paperclip"}))
                (is (eta/has-text? driver {:css ".history__view h1"} "Some context"))
                (is (eta/has-text? driver {:css ".history__view .content p"} "Some edited body goes here"))
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='Attachments:']"}))
                (is (= #{"some-pdf.pdf" "sample.txt"}
                       (into #{}
                             (map (partial eta/get-element-text-el driver))
                             (eta/query-all driver {:css ".history__view ul.attachment-list li"}))))
                (is (= #{:foo :baz/quux}
                       (into #{}
                             (map (comp edn/read-string (partial eta/get-element-text-el driver)))
                             (eta/query-all driver {:css ".history__view .tag-list .tag"}))))
                (ui-utils/click driver {:css ".history__view .panel-heading button.button"})
                (eta/wait-invisible driver {:css ".modal-container.is-active .modal-item.history__view"})))

            (testing "and when showing version 5"
              (ui-utils/click driver {:xpath "(//button[contains(@class,'note__history-show')])[5]"})
              (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})
              (testing "displays the 5th version of the note"
                (is (not (eta/exists? driver {:css ".history__view .lni-paperclip"})))
                (is (eta/has-text? driver {:css ".history__view h1"} "Some context"))
                (is (eta/has-text? driver {:css ".history__view .content p"} "Some edited body goes here"))
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='Attachments:']"}))
                (is (= #{"some-pdf.pdf" "sample.txt" "some other name"}
                       (into #{}
                             (map (partial eta/get-element-text-el driver))
                             (eta/query-all driver {:css ".history__view ul.attachment-list li"}))))
                (is (= #{:foo :baz/quux}
                       (into #{}
                             (map (comp edn/read-string (partial eta/get-element-text-el driver)))
                             (eta/query-all driver {:css ".history__view .tag-list .tag"}))))
                (ui-utils/click driver {:css ".history__view .panel-heading button.button"})
                (eta/wait-invisible driver {:css ".modal-container.is-active .modal-item.history__view"})))

            (testing "and when showing version 6"
              (ui-utils/click driver {:xpath "(//button[contains(@class,'note__history-show')])[6]"})
              (eta/wait-visible driver {:css ".modal-container.is-active .modal-item.history__view"})
              (testing "displays the 6th version of the note"
                (is (not (eta/exists? driver {:css ".history__view .lni-paperclip"})))
                (is (eta/has-text? driver {:css ".history__view h1"} "Some new context"))
                (is (eta/has-text? driver {:css ".history__view .content p"} "Some completely different body"))
                (is (eta/exists? driver {:xpath "//*[contains(@class,'history__view')]//label[text()='Attachments:']"}))
                (is (= #{"sample.txt" "some other name"}
                       (into #{}
                             (map (partial eta/get-element-text-el driver))
                             (eta/query-all driver {:css ".history__view ul.attachment-list li"}))))
                (is (= #{:foo :other/tag}
                       (into #{}
                             (map (comp edn/read-string (partial eta/get-element-text-el driver)))
                             (eta/query-all driver {:css ".history__view .tag-list .tag"}))))
                (ui-utils/click driver {:css ".history__view .panel-heading button.button"})
                (eta/wait-invisible driver {:css ".modal-container.is-active .modal-item.history__view"})))))))))
