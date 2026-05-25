(ns brainard.test.ui.notes.core-test
  (:require
    [brainard.test.ui-system :as ui-sys]
    [brainard.test.ui.utils :as ui-utils]
    [clojure.test :refer [deftest is testing]]
    [etaoin.api :as eta]))

(deftest schedules-test
  (ui-sys/with-system [driver base-url {fix "buzz.edn"}]
    (let [note-sched-id (->> fix
                             (filter (comp #{"Note 1"} :notes/body))
                             first
                             :notes/id)
          note-no-sched-id (->> fix
                                (filter (comp #{"Note 2"} :notes/body))
                                first
                                :notes/id)]
      (testing "when visiting a note with an existing schedule"
        (eta/go driver (str base-url "/notes/" note-sched-id))
        (eta/wait-visible driver {:css "form.schedule-form"})

        (testing "renders the existing schedule"
          (is (eta/exists? driver {:css "p.schedules__header"}))
          (is (not (eta/exists? driver {:css "p.schedules__empty"})))))

      (testing "when visiting a note with no schedules"
        (eta/go driver (str base-url "/notes/" note-no-sched-id))
        (eta/wait-visible driver {:css "form.schedule-form"})

        (testing "renders a notification"
          (is (eta/exists? driver {:css "p.schedules__empty"}))
          (is (= "no related schedules" (eta/get-element-text driver {:css "p.schedules__empty"}))))

        (testing "and when adding a schedule"
          (ui-utils/submit-form! driver "form.schedule-form" {"Day of the week" :monday})
          (eta/wait-invisible driver {:css "p.schedules__empty"})

          (testing "renders the schedule in the list"
            (is (eta/exists? driver {:css "p.schedules__header"}))
            (is (= "Existing schedules" (eta/get-element-text driver {:css "p.schedules__header"})))
            (is (eta/exists? driver {:xpath "//ul[contains(@class,'schedules__items')]//*[text()='monday']"})))

          (testing "and when deleting the schedule"
            (eta/wait-invisible driver {:css "div.message-body"})
            (ui-utils/click driver {:css "button.schedules__delete"})
            (eta/wait-visible driver {:css ".modal-container.is-active .modal-item"})
            (ui-utils/click driver {:css ".modal-container.is-active button.delete-schedule"})

            (testing "removes the schedule from the list"
              (eta/wait-visible driver {:css "p.schedules__empty"})
              (is (not (eta/exists? driver {:css "p.schedules__header"}))))))))))
