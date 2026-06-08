(ns brainard.events.infra.manager-test
  (:require
    [brainard.api.events.interfaces :as ievents]
    [brainard.events.infra.manager :as manager]
    [brainard.infra.test-utils :as tu]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]))

(deftest EventManager-test
  (testing "when creating an EventManager"
    (let [[ch-1 ch-2 ch-3 ch-4] (repeatedly async/chan)
          manager (manager/->EventsManager (ref {}))]
      (testing "and when connecting channels"
        (ievents/connect! manager :ch-1 ch-1)
        (ievents/connect! manager :ch-2 ch-2)

        (testing "and when broadcasting a message"
          (ievents/broadcast! manager :type {:msg 1})
          (testing "sends the message to all channels"
            (is (= [:type {:msg 1}] (tu/<!! ch-1)))
            (is (= [:type {:msg 1}] (tu/<!! ch-2)))))

        (testing "and when disconnecting the channels"
          (ievents/disconnect! manager :ch-1)
          (ievents/disconnect! manager :ch-2)

          (testing "and when broadcasting a message"
            (ievents/broadcast! manager :type {:msg 2})
            (testing "sends no messages"
              (is (nil? (tu/<!! ch-1)))
              (is (nil? (tu/<!! ch-1)))))))

      (testing "and when connecting channels"
        (ievents/connect! manager :ch-3 ch-3)
        (ievents/connect! manager :ch-4 ch-4)

        (testing "and when closing a channel"
          (ievents/disconnect! manager :ch-3)

          (testing "and when broadcasting a message"
            (ievents/broadcast! manager :type {:msg 3})

            (testing "sends messages to open channels"
              (is (nil? (tu/<!! ch-3)))
              (is (= [:type {:msg 3}] (tu/<!! ch-4))))))))))
