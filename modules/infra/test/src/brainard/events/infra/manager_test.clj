(ns brainard.events.infra.manager-test
  (:require
    [brainard.api.events.interfaces :as ievents]
    [brainard.events.infra.manager :as manager]
    [clojure.test :refer [deftest is testing]]))

(deftest create-test
  (testing "when creating an EventManager"
    (let [send-msgs (atom [])
          close-msgs (atom [])
          send-fn (comp (partial swap! send-msgs conj) vector)
          close-fn (partial swap! close-msgs conj)
          manager (manager/create send-fn close-fn)]
      (testing "and when connecting channels"
        (ievents/connect! manager :ch-1 :fake-ch-1)
        (ievents/connect! manager :ch-2 :fake-ch-2)

        (testing "and when broadcasting a message"
          (ievents/broadcast! manager {:msg 1})
          (testing "sends the message to all channels"
            (is (= #{[:fake-ch-1 "event: message\ndata: {:msg 1}\n\n"]
                     [:fake-ch-2 "event: message\ndata: {:msg 1}\n\n"]}
                   (set @send-msgs)))))

        (testing "and when disconnecting the channels"
          (ievents/disconnect! manager :ch-1)
          (ievents/disconnect! manager :ch-2)

          (testing "and when broadcasting a message"
            (reset! send-msgs [])
            (ievents/broadcast! manager {:msg 2})
            (testing "sends no messages"
              (is (= [] @send-msgs))))))

      (testing "and when connecting channels"
        (ievents/connect! manager :ch-3 :fake-ch-3)
        (ievents/connect! manager :ch-4 :fake-ch-4)

        (testing "and when closing a channel"
          (ievents/disconnect! manager :ch-3)

          (testing "and when broadcasting a message"
            (reset! send-msgs [])
            (ievents/broadcast! manager {:msg 3})
            (testing "sends messages to open channels"
              (is (= [[:fake-ch-4 "event: message\ndata: {:msg 3}\n\n"]] @send-msgs)))))))))
