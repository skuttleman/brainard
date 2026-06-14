(ns brainard.test.ui.core.search-test
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

(defn ^:private note-visible? [driver body]
  (let [fmt "//ul[contains(@class,'search-results')]
             //span[contains(@class,'truncate') and text()='%s']"]
    (eta/exists? driver {:xpath (format fmt body)})))

(defn ^:private select-options! [driver label & items]
  (let [item-fmt "//ul[contains(@class,'dropdown-items')]//span[text()='%s']"
        active-fmt "//ul[contains(@class,'dropdown-items')]
                    //li[contains(@class,'is-active')]//span[text()='%s']"]
    (web/click! driver {:css (format ".form-field[data-field-label='%s'] button" label)})
    (eta/wait-visible driver {:css "ul.dropdown-items"})
    (doseq [item-text items]
      (web/click! driver {:xpath (format item-fmt item-text)})
      (web/wait-optimistic #(or (not (eta/exists? driver {:css "ul.dropdown-items"}))
                                (eta/exists? driver {:xpath (format active-fmt item-text)}))))))

(deftest search-tags-test
  (usys/with-webdriver [driver base-url {fix "search.edn"}]
    (letfn [(querying? [qp]
              (let [params (-> (eta/get-url driver) (string/split #"\?") second nav/->query-params)]
                (= qp params)))]
      (testing "when visiting the search page"
        (eta/go driver (str base-url "/search"))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))

        (testing "and when filtering on a tag"
          (select-options! driver "Tag Filter" :tag/alpha)
          (web/click! driver {:css "form.search-form button.submit"})
          (eta/wait-visible driver {:css "ul.search-results"})

          (testing "renders the correct notes"
            (is (note-visible? driver "Note one A"))
            (is (note-visible? driver "Note two A"))
            (is (note-visible? driver "Note one B"))
            (is (not (note-visible? driver "Note two B")))
            (is (not (note-visible? driver "Note three B")))
            (is (not (note-visible? driver "Note one C"))))

          (testing "updates the browser url"
            (is (querying? {:tags "tag/alpha"})))

          (testing "and when clicking the edit link"
            (web/click! driver {:css "ul.search-results > li .note__edit-link"})
            (eta/wait-visible driver {:css ".container.page__note"})
            (testing "renders the note page"
              (let [note-id (-> fix first :notes/id)]
                (is (= (str base-url "/notes/" note-id) (eta/get-url driver)))
                (eta/wait-visible driver {:css ".content"})
                (is (eta/visible? driver {:xpath "//*[contains(@class,'content')]
                                                  //*[text()='Note one A']"})))))

          (testing "and when navigating back"
            (eta/go driver (str base-url "/search"))
            (eta/wait-absent driver {:css "ul.search-results"})
            (testing "clears the search results"
              (is (= (str base-url "/search") (eta/get-url driver)))
              (is (eta/absent? driver {:css "ul.search-results"}))))

          (testing "and when filtering on context"
            (select-options! driver "Topic Filter" "Context A")
            (web/click! driver {:css "form.search-form button.submit"})
            (eta/wait-visible driver {:css "ul.search-results"})

            (testing "renders the correct notes"
              (is (note-visible? driver "Note one A"))
              (is (note-visible? driver "Note two A"))
              (is (not (note-visible? driver "Note one B"))))))

        (testing "and when filtering on multiple tags"
          (eta/go driver (str base-url "/search"))
          (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))
          (select-options! driver "Tag Filter" :tag/alpha :tag/beta)
          (web/click! driver {:css "form.search-form button.submit"})
          (eta/wait-visible driver {:css "ul.search-results"})

          (testing "renders the correct notes"
            (is (note-visible? driver "Note two A"))
            (is (not (note-visible? driver "Note one A")))
            (is (not (note-visible? driver "Note one B")))
            (is (not (note-visible? driver "Note two B")))
            (is (not (note-visible? driver "Note three B"))))

          (testing "updates the browser url"
            (is (querying? {:tags #{"tag/alpha" "tag/beta"}})))

          (testing "and when filtering on context"
            (select-options! driver "Topic Filter" "Context A")
            (web/click! driver {:css "form.search-form button.submit"})
            (eta/wait-visible driver {:css "ul.search-results"})

            (testing "renders the correct notes"
              (is (note-visible? driver "Note two A"))
              (is (not (note-visible? driver "Note two B"))))

            (testing "updates the browser url"
              (is (querying? {:tags #{"tag/alpha" "tag/beta"} :context "Context A"}))))

          (testing "and when the filters yield no results"
            (eta/go driver (str base-url "/search"))
            (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))
            (select-options! driver "Topic Filter" "Context B")
            (select-options! driver "Tag Filter" :tag/alpha :tag/beta)
            (web/click! driver {:css "form.search-form button.submit"})
            (eta/wait-absent driver {:css "ul.search-results"})
            (eta/wait-visible driver {:css "span.search-results"})

            (testing "does not render any notes"
              (is (eta/has-text? driver
                                 {:css ".search-results .message-body"}
                                 "No search results"))
              (is (not (note-visible? driver "Note one A")))
              (is (not (note-visible? driver "Note two A")))
              (is (not (note-visible? driver "Note one B")))
              (is (not (note-visible? driver "Note two B")))
              (is (not (note-visible? driver "Note three B")))
              (is (not (note-visible? driver "Note one C"))))

            (testing "and when including archived notes"
              (web/fill-field! driver "Include archived?" true)
              (web/click! driver {:css "form.search-form button.submit"})
              (eta/wait-absent driver {:css "span.search-results"})
              (eta/wait-visible driver {:css "ul.search-results"})

              (testing "renders the archived note"
                (is (not (note-visible? driver "Note one A")))
                (is (not (note-visible? driver "Note two A")))
                (is (not (note-visible? driver "Note one B")))
                (is (not (note-visible? driver "Note two B")))
                (is (note-visible? driver "Note three B"))
                (is (not (note-visible? driver "Note one C")))))))))))

(deftest search-filters-test
  (usys/with-webdriver [driver base-url {fix "search.edn"}]
    (letfn [(querying? [qp]
              (let [params (-> (eta/get-url driver)
                               (string/split #"\?")
                               second
                               nav/->query-params)]
                (= qp params)))]
      (testing "when visiting the search page"
        (eta/go driver (str base-url "/search"))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))

        (testing "and when filtering on context"
          (eta/go driver (str base-url "/search"))
          (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))
          (select-options! driver "Topic Filter" "Context B")
          (web/click! driver {:css "form.search-form button.submit"})
          (eta/wait-visible driver {:css "ul.search-results"})

          (testing "renders the correct notes"
            (is (note-visible? driver "Note one B"))
            (is (note-visible? driver "Note two B"))
            (is (not (note-visible? driver "Note one A")))
            (is (not (note-visible? driver "Note one C"))))

          (testing "updates the browser url"
            (is (querying? {:context "Context B"}))))

        (testing "and when filtering on TODOs"
          (eta/go driver (str base-url "/search"))
          (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))

          (testing "and when the filter is :complete"
            (select-options! driver "TODO Filter" "Complete (1+ all finished)")
            (web/click! driver {:css "form.search-form button.submit"})
            (eta/wait-visible driver {:css "ul.search-results"})

            (testing "renders the correct notes"
              (is (not (note-visible? driver "Note one A")))
              (is (not (note-visible? driver "Note two A")))
              (is (not (note-visible? driver "Note one A")))
              (is (not (note-visible? driver "Note one B")))
              (is (not (note-visible? driver "Note two B")))
              (is (note-visible? driver "Note one C"))))

          (testing "and when the filter is :incomplete"
            (select-options! driver "TODO Filter" "Incomplete (1+ unfinished)")
            (web/click! driver {:css "form.search-form button.submit"})
            (eta/wait-visible driver {:css "ul.search-results"})

            (testing "renders the correct notes"
              (is (note-visible? driver "Note one A"))
              (is (not (note-visible? driver "Note two A")))
              (is (not (note-visible? driver "Note one B")))
              (is (note-visible? driver "Note two B"))
              (is (not (note-visible? driver "Note one C"))))

            (testing "and when filtering on tags"
              (select-options! driver "Tag Filter" :tag/alpha)
              (web/click! driver {:css "form.search-form button.submit"})
              (eta/wait-visible driver {:css "ul.search-results"})

              (testing "renders the correct notes"
                (is (note-visible? driver "Note one A"))
                (is (not (note-visible? driver "Note two A")))
                (is (not (note-visible? driver "Note one B")))
                (is (not (note-visible? driver "Note two B")))
                (is (not (note-visible? driver "Note three B")))
                (is (not (note-visible? driver "Note one C"))))

              (testing "and when including archived notes"
                (web/fill-field! driver "Include archived?" true)
                (web/click! driver {:css "form.search-form button.submit"})
                (web/wait-optimistic #(eta/visible? driver {:css ".loader"}))
                (eta/wait-absent driver {:css ".loader"})

                (testing "renders the archived note"
                  (is (note-visible? driver "Note one A"))
                  (is (not (note-visible? driver "Note two A")))
                  (is (not (note-visible? driver "Note one B")))
                  (is (not (note-visible? driver "Note two B")))
                  (is (note-visible? driver "Note three B"))
                  (is (not (note-visible? driver "Note one C"))))

                (testing "and when restoring the archived note"
                  (web/click! driver {:css ".search-results .note__restore-button"})
                  (web/wait-optimistic #(eta/visible? driver {:css ".page__note"}))

                  (testing "renders the note page"
                    (let [note-id (-> fix peek :notes/id)]
                      (is (= (str base-url "/notes/" note-id) (eta/get-url driver)))
                      (eta/wait-visible driver {:css ".content"})
                      (is (eta/visible? driver {:xpath "//*[contains(@class,'content')]
                                                        //*[text()='Note three B']"})))))))))))))

(deftest search-fulltext-test
  (usys/with-webdriver [driver base-url {_ "search.edn"}]
    (testing "when visiting the search page"
      (eta/go driver (str base-url "/search"))
      (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))

      (testing "and when filtering on body text"
        (eta/go driver (str base-url "/search"))
        (web/wait-optimistic #(eta/visible? driver {:css ".page__search"}))
        (web/submit-form! driver "form.search-form" {"Body contents" "one"})
        (eta/wait-visible driver {:css "ul.search-results"})

        (testing "renders the correct notes"
          (is (note-visible? driver "Note one A"))
          (is (not (note-visible? driver "Note two A")))
          (is (note-visible? driver "Note one B"))
          (is (not (note-visible? driver "Note two B")))
          (is (note-visible? driver "Note one C")))

        (testing "and when filtering on tags"
          (select-options! driver "Tag Filter" :tag/alpha)
          (web/click! driver {:css "form.search-form button.submit"})
          (eta/wait-visible driver {:css "ul.search-results"})

          (testing "renders the correct notes"
            (is (note-visible? driver "Note one A"))
            (is (not (note-visible? driver "Note two A")))
            (is (note-visible? driver "Note one B"))
            (is (not (note-visible? driver "Note two B")))
            (is (not (note-visible? driver "Note one C"))))

          (testing "and when filtering on context"
            (select-options! driver "Topic Filter" "Context B")
            (web/click! driver {:css "form.search-form button.submit"})
            (eta/wait-visible driver {:css "ul.search-results"})

            (testing "renders the correct notes"
              (is (not (note-visible? driver "Note one A")))
              (is (not (note-visible? driver "Note two A")))
              (is (note-visible? driver "Note one B"))
              (is (not (note-visible? driver "Note two B")))
              (is (not (note-visible? driver "Note three B")))
              (is (not (note-visible? driver "Note one C"))))

            (testing "and when including archived notes"
              (web/fill-field! driver "Include archived?" true)
              (web/click! driver {:css "form.search-form button.submit"})
              (web/wait-optimistic #(eta/visible? driver {:css ".loader"}))
              (eta/wait-absent driver {:css ".loader"})

              (testing "renders the correct notes"
                (is (not (note-visible? driver "Note one A")))
                (is (not (note-visible? driver "Note two A")))
                (is (note-visible? driver "Note one B"))
                (is (not (note-visible? driver "Note two B")))
                (is (not (note-visible? driver "Note three B")))
                (is (not (note-visible? driver "Note one C")))))))))))

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
          (is (not (note-visible? driver "Note 2")))
          (is (not (note-visible? driver "Note 3"))))

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
                (is (note-visible? driver "Note 2"))
                (is (not (note-visible? driver "Note 3")))))))))))
