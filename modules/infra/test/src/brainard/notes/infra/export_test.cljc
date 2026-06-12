(ns brainard.notes.infra.export-test
  (:require
   [brainard.notes.infra.export :as export]
   [clojure.test :refer [deftest is testing]]
   [slag.utils.uuids :as uuids]))

(deftest ->markdown-test
  (testing "when rendering a note"
    (let [[note-id a1 a2 a3 t1 t4] (sort (take 6 (repeatedly uuids/random)))
          md (export/->markdown "http://example.com"
                                {:notes/id          note-id
                                 :notes/context     "Some Context"
                                 :notes/body        "a body"
                                 :notes/pinned?     true
                                 :notes/tags        (shuffle [:foo :bar :baz/quux :bar/other])
                                 :notes/attachments (shuffle [{:attachments/id   a1
                                                               :attachments/name "foo.txt"}
                                                              {:attachments/id   a2
                                                               :attachments/name "bar.txt"}
                                                              {:attachments/id   a3
                                                               :attachments/name "foo.txt"}])
                                 :notes/todos       (shuffle [{:todos/id         t1
                                                               :todos/completed? true
                                                               :todos/text       "fee"}
                                                              {:todos/id         t1
                                                               :todos/completed? false
                                                               :todos/text       "fi"}
                                                              {:todos/id         t1
                                                               :todos/completed? true
                                                               :todos/text       "foe"}
                                                              {:todos/id         t4
                                                               :todos/completed? false
                                                               :todos/text       "fum"}])})]
      (testing "renders the note as expected"
        (is (= (str "# Some Context [Pinned]" \newline \newline
                    "a body" \newline \newline
                    "<ul style=\"list-style:none;padding-left:0;\">"
                    "<li style=\"background-color:rgba(50, 50, 50, 100);border-radius:3px;color:lightblue;display:inline-block;margin-right:8px;padding:2px 4px;\">:bar</li>"
                    "<li style=\"background-color:rgba(50, 50, 50, 100);border-radius:3px;color:lightblue;display:inline-block;margin-right:8px;padding:2px 4px;\">:foo</li>"
                    "<li style=\"background-color:rgba(50, 50, 50, 100);border-radius:3px;color:lightblue;display:inline-block;margin-right:8px;padding:2px 4px;\">:bar/other</li>"
                    "<li style=\"background-color:rgba(50, 50, 50, 100);border-radius:3px;color:lightblue;display:inline-block;margin-right:8px;padding:2px 4px;\">:baz/quux</li>"
                    "</ul>" \newline \newline
                    "## TODOs" \newline \newline
                    "- [x] fee" \newline
                    "- [x] foe" \newline
                    "- [ ] fi" \newline
                    "- [ ] fum" \newline \newline
                    "## Attachments" \newline \newline
                    "- <a href=\"http://example.com/attachments/" a2 "\" target=\"_blank\">bar.txt</a>" \newline
                    "- <a href=\"http://example.com/attachments/" a1 "\" target=\"_blank\">foo.txt</a>" \newline
                    "- <a href=\"http://example.com/attachments/" a3 "\" target=\"_blank\">foo.txt</a>")
               md))))

    (testing "and when the note is barebones"
      (let [note-id (uuids/random)
            md (export/->markdown "http://example.com"
                                  {:notes/id      note-id
                                   :notes/context "Some Context"
                                   :notes/body    "## My body\n\nis all about **bling**!\n"
                                   :notes/pinned? false})]
        (testing "renders the note as expected"
          (is (= (str "# Some Context" \newline \newline
                      "## My body" \newline \newline
                      "is all about **bling**!")
                 md)))))))
