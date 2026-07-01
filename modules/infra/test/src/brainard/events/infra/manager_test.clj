(ns brainard.events.infra.manager-test
  (:require
   [brainard.api.events.interfaces :as ievents]
   [brainard.events.infra.manager :as manager]
   [clojure.core.async :as async]
   [clojure.test :refer [deftest is testing]]
   [slag.test.utils.async :as tua]))

(deftest EventManager-test
  (testing "when creating an EventManager"
    (let [[ch-1 ch-2 ch-3 ch-4] (repeatedly async/chan)
          manager (manager/create 100)]
      (testing "and when connecting channels"
        (ievents/connect! manager :ch-1 {:ch ch-1})
        (ievents/connect! manager :ch-2 {:ch ch-2})

        (testing "and when broadcasting a message"
          (ievents/broadcast! manager :type {:msg 1})
          (testing "sends the message to all channels"
            (is (= [:message [:type {:msg 1}]] (tua/<!! ch-1)))
            (is (= [:message [:type {:msg 1}]] (tua/<!! ch-2)))))

        (testing "and when disconnecting the channels"
          (ievents/disconnect! manager :ch-1)
          (ievents/disconnect! manager :ch-2)

          (testing "and when broadcasting a message"
            (ievents/broadcast! manager :type {:msg 2})
            (testing "sends no messages"
              (is (nil? (tua/<!! ch-1)))
              (is (nil? (tua/<!! ch-1)))))))

      (testing "and when connecting channels"
        (ievents/connect! manager :ch-3 {:ch ch-3})

        (testing "broadcasts unexpired cached messages"
          (is (= [:message [:type {:msg 1}]] (tua/<!! ch-3)))
          (is (= [:message [:type {:msg 2}]] (tua/<!! ch-3))))

        (testing "does not broadcast expired cached messages"
          (async/<!! (async/timeout 200))
          (ievents/connect! manager :ch-4 {:ch ch-4})
          (is (= ::empty (tua/<!! ch-4 10 ::empty))))

        (testing "and when closing a channel"
          (ievents/disconnect! manager :ch-3)

          (testing "and when broadcasting a message"
            (ievents/broadcast! manager :type {:msg 3})

            (testing "sends messages to open channels"
              (is (nil? (tua/<!! ch-3)))
              (is (= [:message [:type {:msg 3}]] (tua/<!! ch-4))))))))))
