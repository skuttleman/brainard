(ns brainard.test.ui.notes.core-test
  (:require
    [brainard.test.ui-system :as ui-sys]
    [brainard.test.ui.utils :as ui-utils]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest schedules-test
  (ui-sys/with-system [driver base-url {fix "buzz.edn"}]
    (let [note-with-sched-id (->> fix
                                  (filter (comp #{"Note 1"} :notes/body))
                                  first
                                  :notes/id)
          note-without-sched-id (->> fix
                                     (filter (comp #{"Note 2"} :notes/body))
                                     first
                                     :notes/id)]
      (testing "when visiting a note with an existing schedule"
        (eta/go driver (str base-url "/notes/" note-with-sched-id))
        (eta/wait-visible driver {:css "form.schedule-form"})

        (testing "renders the existing schedule"
          (is (eta/exists? driver {:css "p.existing-schedules"}))
          (is (not (eta/exists? driver {:css "p.no-schedules"})))))

      (testing "when visiting a note with no schedules"
        (eta/go driver (str base-url "/notes/" note-without-sched-id))
        (eta/wait-visible driver {:css "form.schedule-form"})

        (testing "renders 'no related schedules'"
          (is (eta/exists? driver {:css "p.no-schedules"})))

        (testing "and when adding a schedule"
          (ui-utils/submit-form! driver "form.schedule-form" {"Day of the week" :monday})
          (eta/wait-invisible driver {:css "p.no-schedules"})

          (testing "renders the schedule in the list"
            (is (eta/exists? driver {:css "p.existing-schedules"}))
            (is (eta/exists? driver {:xpath "//em[contains(@class,'blue') and text()='monday']"})))

          (testing "and when deleting the schedule"
            (eta/wait-invisible driver {:css "div.message-body"})
            (ui-utils/click driver {:css "ul.layout--stack-between li button.is-danger"})
            (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
            (ui-utils/click driver {:css ".modal-container.is-active button.is-info"})

            (testing "removes the schedule from the list"
              (eta/wait-visible driver {:css "p.no-schedules"})
              (is (not (eta/exists? driver {:css "p.existing-schedules"}))))))))))
